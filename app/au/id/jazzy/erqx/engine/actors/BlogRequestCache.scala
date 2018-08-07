package au.id.jazzy.erqx.engine.actors

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.Materializer
import au.id.jazzy.erqx.engine.controllers.BlogRequest
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc.{Headers, RequestHeader, ResponseHeader, Result}
import play.api.http.HeaderNames._
import play.api.mvc.Results._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import play.api.http.{HttpEntity, Status}
import play.api.libs.streams.GzipFlow
import play.filters.gzip.GzipFilterConfig
import akka.pattern.pipe
import au.id.jazzy.erqx.engine.models.CacheConfig

import scala.concurrent.{ExecutionContext, Future}

object BlogRequestCache {

  case class ExecuteRequest[A](blogRequest: BlogRequest[A], action: BlogRequest[A] => Future[Result])

  private case class ResultForKey(cacheKey: RequestCacheKey, result: Result)
  private case class RequestCacheKey(blogHash: String, method: String, uri: String, language: Lang, gzip: Boolean) {
    override def toString = s"RequestCacheKey($method $uri)"
  }
  private class CachedResult(val result: Result) {
    var lastUsed = System.currentTimeMillis()

    def touch() = {
      lastUsed = System.currentTimeMillis()
    }

    def size = {
      result.body match {
        case HttpEntity.Strict(bytes, _) => bytes.size
        case _ => 0 // Placeholder for streamed HEAD responses
      }
    }
  }

  def props(messagesApi: MessagesApi, cacheConfig: CacheConfig, gzipConfig: GzipFilterConfig)(implicit mat: Materializer): Props = {
    Props(new BlogRequestCache(messagesApi, cacheConfig, gzipConfig))
  }
}

/**
  * Executes and caches the results of requests.
  *
  * @param lowWatermark When entries from the cache are expired, it will be drained to this value.
  * @param highWatermark When the cache reaches this size, it will be drained to the low water mark.
  */
private class BlogRequestCache(messagesApi: MessagesApi, cacheConfig: CacheConfig, gzipConfig: GzipFilterConfig)(implicit mat: Materializer) extends Actor with ActorLogging {

  private val gzip = new GzipEncoding(gzipConfig)
  private val lowWatermark = cacheConfig.lowWatermark.toBytes
  private val highWatermark = cacheConfig.highWatermark.toBytes
  import context.dispatcher

  import BlogRequestCache._

  // An estimate of the response header size
  private val ResponseHeaderSize = 100

  // The number of bytes in the cache. This is based on the size of the body, and an approximate guess for the size of
  // each response header
  private var cacheSize = 0l
  private var requestCache = Map.empty[RequestCacheKey, CachedResult]

  override def receive = {
    case ExecuteRequest(request, action) =>
      checkNotModified(request, action)

    case ResultForKey(cacheKey, result) =>
      sender() ! result
      maybeCache(cacheKey, result)

    case failure @ Failure(_) =>
      sender() ! failure
  }


  private def checkNotModified[A](request: BlogRequest[A], action: BlogRequest[A] => Future[Result]) = {
    // etag - take 7 characters of the blog hash and 7 characters of the theme hash. So if either the theme
    // changes, or the blog changes, everything served by the blog will expire.
    val etag = "\"" + request.blog.hash.take(7) + request.blog.info.theme.hash.take(7) + "\""
    if (request.headers.get(IF_NONE_MATCH).contains(etag)) {
      sender() ! NotModified
    } else {
      checkRequestCache(request, action, etag)
    }
  }

  private def checkRequestCache[A](request: BlogRequest[A], action: BlogRequest[A] => Future[Result], etag: String) = {
    val cacheKey = RequestCacheKey(request.blog.hash, request.method, request.uri, messagesApi.preferred(request).lang,
      gzip.mayCompress(request))

    requestCache.get(cacheKey) match {

      case Some(resultCache) =>
        log.debug("Cache hit on {}", cacheKey)
        sender() ! resultCache.result
        resultCache.touch()

      case None =>
        log.debug("Cache miss on {}", cacheKey)
        val gzippedResult = if (cacheKey.gzip) {
          action(request).flatMap(gzip.handleResult(request, _))
        } else {
          action(request)
        }

        val withEtagResult = gzippedResult.map { result =>
          ResultForKey(cacheKey,
            result.withHeaders(ETAG -> etag)
              .withDateHeaders(LAST_MODIFIED -> request.blog.lastUpdated)
          )
        }

        withEtagResult.pipeTo(self)(sender())
    }

  }

  private def maybeCache(cacheKey: RequestCacheKey, result: Result) = {
    // Only cache strict entities, anything else could be stateful so can't be cached.
    result.body match {
      case HttpEntity.Strict(bytes, _) =>
        requestCache += (cacheKey -> new CachedResult(result))
        cacheSize += bytes.size + ResponseHeaderSize

        log.debug("Cached {} with {} bytes, cache is now {} bytes", cacheKey, bytes.size, cacheSize)

      case HttpEntity.Streamed(data, contentLength, contentType) if cacheKey.method == "HEAD" =>
        // Cache with an empty streamed body because it's a head request
        data.runWith(Sink.cancelled)
        requestCache += (cacheKey -> new CachedResult(
          result.copy(body = HttpEntity.Streamed(Source.empty, contentLength, contentType))
        ))
        cacheSize += ResponseHeaderSize

        log.debug("Cached {} with empty source, cache is now {} bytes", cacheKey, cacheSize)

      case _ => // don't cache
    }

    if (cacheSize > highWatermark) {
      drainCacheToLowWatermark()
    }
  }

  private def drainCacheToLowWatermark() = {
    val sortedCache = requestCache.toList.sortBy(_._2.lastUsed)

    def drain(items: List[(RequestCacheKey, CachedResult)]): Unit = {
      if (cacheSize > lowWatermark) {
        cacheSize -= (items.head._2.size + ResponseHeaderSize)
        requestCache -= items.head._1
        drain(items.tail)
      }
    }

    drain(sortedCache)

    log.debug("Drained cache to {} items with {} bytes", requestCache.size, cacheSize)
  }

}

// Mostly copied from the Gzip filter
class GzipEncoding(config: GzipFilterConfig)(implicit mat: Materializer) {

  def handleResult(request: RequestHeader, result: Result): Future[Result] = {
    implicit val ec = mat.executionContext
    if (shouldCompress(result) && config.shouldGzip(request, result)) {

      val header = result.header.copy(headers = setupHeader(result.header.headers))

      result.body match {

        case HttpEntity.Strict(data, contentType) =>
          compressStrictEntity(Source.single(data), contentType).map(entity =>
            result.copy(header = header, body = entity)
          )

        case entity @ HttpEntity.Streamed(_, Some(contentLength), contentType) if contentLength <= config.chunkedThreshold =>
          // It's below the chunked threshold, so buffer then compress and send
          compressStrictEntity(entity.data, contentType).map(strictEntity =>
            result.copy(header = header, body = strictEntity)
          )

        case other =>
          Future.successful(result)
      }
    } else {
      Future.successful(result)
    }
  }

  private def compressStrictEntity(source: Source[ByteString, Any], contentType: Option[String])(implicit ec: ExecutionContext) = {
    val compressed = source.via(GzipFlow.gzip(config.bufferSize)).runFold(ByteString.empty)(_ ++ _)
    compressed.map(data => HttpEntity.Strict(data, contentType))
  }

  /**
    * Whether this request may be compressed.
    */
  def mayCompress(request: RequestHeader) =
    request.method != "HEAD" && gzipIsAcceptedAndPreferredBy(request)


  /**
    * Whether this response should be compressed.  Responses that may not contain content won't be compressed, nor will
    * responses that already define a content encoding.  Empty responses also shouldn't be compressed, as they will
    * actually always get bigger.
    */
  private def shouldCompress(result: Result) = isAllowedContent(result.header) &&
    isNotAlreadyCompressed(result.header) &&
    !result.body.isKnownEmpty

  /**
    * Certain response codes are forbidden by the HTTP spec to contain content, but a gzipped response always contains
    * a minimum of 20 bytes, even for empty responses.
    */
  private def isAllowedContent(header: ResponseHeader) = header.status != Status.NO_CONTENT && header.status != Status.NOT_MODIFIED

  /**
    * Of course, we don't want to double compress responses
    */
  private def isNotAlreadyCompressed(header: ResponseHeader) = header.headers.get(CONTENT_ENCODING).isEmpty

  private def setupHeader(header: Map[String, String]): Map[String, String] = {
    header + (CONTENT_ENCODING -> "gzip") + addToVaryHeader(header, VARY, ACCEPT_ENCODING)
  }

  /**
    * There may be an existing Vary value, which we must add to (comma separated)
    */
  private def addToVaryHeader(existingHeaders: Map[String, String], headerName: String, headerValue: String): (String, String) = {
    existingHeaders.get(headerName) match {
      case None => (headerName, headerValue)
      case Some(existing) if existing.split(",").exists(_.trim.equalsIgnoreCase(headerValue)) => (headerName, existing)
      case Some(existing) => (headerName, s"$existing,$headerValue")
    }
  }

  private def acceptHeader(headers: Headers, headerName: String): Seq[(Double, String)] = {
    for {
      header <- headers.get(headerName).toList
      value0 <- header.split(',')
      value = value0.trim
    } yield {
      RequestHeader.qPattern.findFirstMatchIn(value) match {
        case Some(m) => (m.group(1).toDouble, m.before.toString)
        case None => (1.0, value) // “The default value is q=1.”
      }
    }
  }

  private def gzipIsAcceptedAndPreferredBy(request: RequestHeader) = {
    val codings = acceptHeader(request.headers, ACCEPT_ENCODING)
    def explicitQValue(coding: String) = codings collectFirst { case (q, c) if c equalsIgnoreCase coding => q }
    def defaultQValue(coding: String) = if (coding == "identity") 0.001d else 0d
    def qvalue(coding: String) = explicitQValue(coding) orElse explicitQValue("*") getOrElse defaultQValue(coding)

    qvalue("gzip") > 0d && qvalue("gzip") >= qvalue("identity")
  }


}

