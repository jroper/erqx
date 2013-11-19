package actors

import akka.actor.{ActorRef, Props, Actor}
import java.io.File
import models.{GitConfig, BlogConfig}

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
      val blogConfigs = List(
        BlogConfig("default", GitConfig(new File(
          Option(System.getProperty("blog.path")).getOrElse("/Users/jroper/tmp/theblog")
        ), "master", None), "")
      )

      val blogs = blogConfigs.map { config =>
        config -> context.actorOf(Props(new BlogActor(config.gitConfig, config.path)), config.name)
      }

      sender ! BlogsLoaded(blogs)
    }
  }

}
