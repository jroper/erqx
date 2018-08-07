package au.id.jazzy.erqx.engine

import javax.inject.{Inject, Singleton}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import au.id.jazzy.erqx.engine.actors.{BlogActor, BlogRequestCache}
import au.id.jazzy.erqx.engine.models.{BlogConfig, ErqxConfig}
import play.api.i18n.MessagesApi
import play.api.{Environment, Logger}
import play.filters.gzip.GzipFilterConfig

/**
 * Loads all the blogs.
 */
@Singleton
class Blogs @Inject() (environment: Environment, erqxConfig: ErqxConfig, system: ActorSystem,
  messagesApi: MessagesApi, gzipFilterConfig: GzipFilterConfig)(implicit mat: Materializer) {

  lazy val blogs: Seq[(BlogConfig, ActorRef)] = {
    val blogs = {
      erqxConfig.blogs.map { config =>
        val actor = system.actorOf(Props(new BlogActor(config.gitConfig, config.path, environment.classLoader)), "blog-" + config.name)
        config -> actor
      }
    }

    val sorted = blogs.sortBy(_._1.order)

    Logger.info("Started blogs: " + sorted.map { blog =>
      blog._1.name + ":" + blog._1.path
    }.mkString(", "))

    sorted
  }

  lazy val blogRequestCache: ActorRef = {
    system.actorOf(BlogRequestCache.props(messagesApi, erqxConfig.cache, gzipFilterConfig), "blogs-request-cache")
  }

}
