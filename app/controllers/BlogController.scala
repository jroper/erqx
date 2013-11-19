package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import models._
import actors.BlogActor._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => sync}
import scala.concurrent.duration._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import org.joda.time.YearMonth
import java.io.InputStream
import play.api.libs.iteratee.Enumerator
import play.api.libs.MimeTypes

class BlogController(blogActor: ActorRef, val router: BlogReverseRouter) extends Controller {

  implicit val defaultTimeout = Timeout(5 seconds)

  def index(page: Page) = paged(page, None)(_.posts)(router.index)

  def year(year: Int, page: Page) =
    paged(page, Some("All posts for " + year))(
      _.forYear(year).posts
    )(p => router.year(year, p))

  def month(year: Int, month: Int, page: Page) =
    paged(page, Some(
      "All posts for " + monthName(year, month) + " " + year
    ))(
      _.forYear(year).forMonth(month).posts
    )(p => router.month(year, month, p))

  def day(year: Int, month: Int, day: Int, page: Page) =
    paged(page, Some(
      "All posts for " + day + " " + monthName(year, month) + " " + year
    ))(
      _.forYear(year).forMonth(month).forDay(day).posts
    )(p => router.day(year, month, day, p))

  def tag(tag: String, page: Page) =
    paged(page, Some("All posts with tag " + tag))(
      _.forTag(tag).getOrElse(Nil)
    )(p => router.tag(tag, p))

  def view(year: Int, month: Int, day: Int, permalink: String) = BlogAction.async { req =>
    req.blog.forYear(year).forMonth(month).forDay(day).forPermalink(permalink) match {
      case Some(post) =>
        (blogActor ? RenderPost(req.blog, post)).mapTo[Option[String]].map {
          case Some(rendered) =>
            Ok(views.html.blogPost(req.blog, router, post, rendered))
          case None => notFound
        }
      case None => sync(notFound)
    }
  }

  def asset(path: String) = BlogAction.async { req =>
    (blogActor ? LoadStream(req.blog, path)).mapTo[Option[FileStream]].map {
      case Some(FileStream(length, is, ec)) =>
        val result = SimpleResult(ResponseHeader(OK, Map(
          CONTENT_LENGTH -> length.toString
        )), Enumerator.fromStream(is)(ec))

        MimeTypes.forFileName(path).map(mt => result.withHeaders(CONTENT_TYPE -> mt)).getOrElse(result)

      case None => notFound
    }
  }

  def paged(p: Page, title: Option[String])
           (getPosts: Blog => List[BlogPost])
           (route: (Page) => Call) = BlogAction.async { req =>

    val page = if (p.page < 1) 1 else p.page
    val perPage = if (p.perPage < 1) 1 else if (p.perPage > 10) 10 else p.perPage

    val zeroBasedPage = page - 1

    val allPosts = getPosts(req.blog)
    val posts = allPosts.drop(zeroBasedPage * perPage).take(perPage)
    val lastPage = allPosts.size / perPage
    val previous = if (page > 1) Some(route(Page(page - 1, perPage))) else None
    val next = if (page < lastPage) Some(route(Page(page + 1, perPage))) else None

    // Load blog posts
    Future.sequence(posts.map { post =>
      (blogActor ? RenderPost(req.blog, post)).mapTo[Option[String]].map(_.map(post -> _))
    }).map { loaded =>
      Ok(views.html.blogPosts(req.blog, router, title, loaded.flatMap(_.toSeq), previous, next))
    }
  }

  def notFound = NotFound("Not Found")

  private def monthName(year: Int, month: Int) = scala.util.control.Exception.allCatch.opt(
    new YearMonth(year, month).monthOfYear().getAsText
  ).getOrElse("" + month)

  /**
   * Action builder for blog requests. Loads the current blog, as well as handles etag caching headers
   */
  object BlogAction extends ActionBuilder[BlogRequest] {

    protected def invokeBlock[A](request: Request[A], block: (BlogRequest[A]) => Future[SimpleResult]) = {
      (blogActor ? GetBlog).mapTo[Blog].flatMap { blog =>
        if (request.headers.get(IF_NONE_MATCH).exists(_ == (blog.hash + BlogController.startTime))) {
          sync(NotModified)
        } else {
          block(new BlogRequest(request, blog)).map(_.withHeaders(ETAG -> (blog.hash + BlogController.startTime)))
        }
      }
    }
  }
}

object BlogController {
  val startTime = System.currentTimeMillis()
}

class BlogRequest[A](request: Request[A], val blog: Blog) extends WrappedRequest[A](request)

case class Page(page: Int, perPage: Int)