package au.id.jazzy.erqx.engine

import play.api._
import java.io.File
import akka.util.Timeout
import akka.actor.Props
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._
import au.id.jazzy.erqx.engine.actors.BlogsActor
import au.id.jazzy.erqx.engine.actors.BlogsActor._
import au.id.jazzy.erqx.engine.controllers.BlogsRouter
import au.id.jazzy.erqx.engine.models.{GitConfig, BlogConfig}
import play.api.libs.concurrent.Akka

class BlogPlugin(app: Application) extends Plugin {

  override def onStart() = {
    val system = Akka.system(app)

    val blogConfigs = app.configuration.getConfig("blogs").map { bcs =>
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
            ), gc.getInt("order").getOrElse(10))
          }
        }
      }
    }.toList.flatten.sortBy(_.order)

    implicit val timeout = Timeout(1 minute)
    val BlogsLoaded(blogs) = Await.result(
      (system.actorOf(Props[BlogsActor], "blogs") ? LoadBlogs(blogConfigs)).mapTo[BlogsLoaded],
      1 minute)

    BlogsRouter.startBlogs(blogs.sortBy(_._1.order))

    Logger.info("Started blogs: " + blogs.map { blog =>
      blog._1.name + ":" + blog._1.path
    }.mkString(", "))
  }
}
