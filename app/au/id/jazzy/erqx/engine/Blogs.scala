package au.id.jazzy.erqx.engine

import java.io.File
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSelection, ActorSystem, Props}
import akka.stream.Materializer
import au.id.jazzy.erqx.engine.actors.{BlogRequestCache, BlogsActor}
import au.id.jazzy.erqx.engine.models.{BlogConfig, GitConfig}
import com.typesafe.config.ConfigMemorySize
import play.api.i18n.MessagesApi
import play.api.{Configuration, Environment, Logger}
import play.filters.gzip.GzipFilterConfig

import scala.concurrent.duration.FiniteDuration

/**
 * Loads all the blogs.
 */
@Singleton
class Blogs @Inject() (environment: Environment, configuration: Configuration, system: ActorSystem,
  messagesApi: MessagesApi, gzipFilterConfig: GzipFilterConfig)(implicit mat: Materializer) {

  lazy val blogs: Seq[(BlogConfig, ActorSelection)] = {

    val blogConfigs = configuration.getPrototypedMap("blogs", "erqx.blogs.prototype").map {
      case (name, blogConfig) =>
        val path = blogConfig.get[String]("path")
        val gitConfig = blogConfig.get[Configuration]("gitConfig")
        val order = blogConfig.get[Int]("order")

        BlogConfig(name, path,
          GitConfig(
            name,
            new File(gitConfig.get[String]("gitRepo")),
            gitConfig.get[Option[String]]("path"),
            gitConfig.get[String]("branch"),
            gitConfig.get[Option[String]]("remote"),
            gitConfig.get[Option[String]]("fetchKey"),
            gitConfig.get[Option[FiniteDuration]]("updateInterval")
          ),
          order
        )
    }.toList.sortBy(_.order)

    val blogs = {
      val blogsActor = system.actorOf(Props(new BlogsActor(blogConfigs, environment.classLoader)), "blogs")
      blogConfigs.map { config =>
        config -> system.actorSelection(blogsActor.path / config.name)
      }
    }

    val sorted = blogs.sortBy(_._1.order)

    Logger.info("Started blogs: " + sorted.map { blog =>
      blog._1.name + ":" + blog._1.path
    }.mkString(", "))

    sorted
  }

  lazy val blogRequestCache = {
    val lowWatermark = configuration.get[ConfigMemorySize]("erqx.cache.low-watermark")
    val highWatermark = configuration.get[ConfigMemorySize]("erqx.cache.high-watermark")
    system.actorOf(BlogRequestCache.props(messagesApi, lowWatermark.toBytes, highWatermark.toBytes, gzipFilterConfig), "blog-request-cache")
  }

}
