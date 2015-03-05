package au.id.jazzy.erqx.engine

import java.io.File
import javax.inject.{Singleton, Inject}

import akka.actor.{ActorSelection, ActorSystem, Props}
import au.id.jazzy.erqx.engine.actors.BlogsActor
import au.id.jazzy.erqx.engine.models.{BlogConfig, GitConfig}
import play.api.{Configuration, Logger}

/**
 * Loads all the blogs.
 */
@Singleton
class Blogs @Inject() (configuration: Configuration, system: ActorSystem) {

  lazy val blogs: Seq[(BlogConfig, ActorSelection)] = {

    val blogConfigs = configuration.getConfig("blogs").map { bcs =>
      bcs.subKeys.flatMap { name =>
        bcs.getConfig(name).flatMap { blogConfig =>
          val path = blogConfig.getString("path").getOrElse("/blog")
          blogConfig.getConfig("gitConfig").map { gc =>
            BlogConfig(name, path, GitConfig(
              name,
              new File(gc.getString("gitRepo").getOrElse(".")),
              gc.getString("path"),
              gc.getString("branch").getOrElse("published"),
              gc.getString("remote"),
              gc.getString("fetchKey"),
              gc.getMilliseconds("updateInterval")
            ), blogConfig.getInt("order").getOrElse(10))
          }
        }
      }
    }.toList.flatten.sortBy(_.order)

    val blogs = {
      val blogsActor = system.actorOf(Props(new BlogsActor(blogConfigs)), "blogs")
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

}
