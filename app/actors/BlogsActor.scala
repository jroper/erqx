package actors

import akka.actor.{ActorRef, Props, Actor}
import java.io.File
import models._
import play.api.Play

object BlogsActor {
  case object LoadBlogs
  case class BlogsLoaded(blogs: List[(BlogConfig, ActorRef)])
}

/**
 * Actor responsible for starting some blogs
 */
class BlogsActor extends Actor {

  import actors.BlogsActor._

  def receive = {
    case LoadBlogs => {
      val blogConfigs = Play.current.configuration.getConfig("blogs").map { bcs =>
        bcs.subKeys.flatMap { name =>
          bcs.getConfig(name).flatMap { blogConfig =>
            val path = blogConfig.getString("path").getOrElse("/blog")
            blogConfig.getConfig("gitConfig").map { gc =>
              BlogConfig(name, path, GitConfig(
                new File(gc.getString("gitRepo").getOrElse(".")),
                gc.getString("branch").getOrElse("published"),
                gc.getString("remote"),
                gc.getString("fetchKey")
              ))
            }
          }
        }
      }.toList.flatten

      val blogs = blogConfigs.map { config =>
        config -> context.actorOf(Props(new BlogActor(config.gitConfig, config.path)), config.name)
      }

      sender ! BlogsLoaded(blogs)
    }
  }

}
