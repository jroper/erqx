package au.id.jazzy.erqx.engine.actors

import akka.actor.{ActorRef, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.actors.BlogActor.GetBlog
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.actor.Status.Failure

object BlogsActor {
  case class LoadBlogs(blogConfigs: List[BlogConfig])
  case class BlogsLoaded(blogs: List[(BlogConfig, ActorRef)])
}

/**
 * Actor responsible for starting some blogs
 */
class BlogsActor extends Actor {

  import BlogsActor._

  def receive = {
    case LoadBlogs(blogConfigs) => {

      import context.dispatcher
      implicit val timeout = Timeout(1 minute)

      val theSender = sender
      Future.sequence(blogConfigs.map { config =>
        val actor = context.actorOf(Props(new BlogActor(config.gitConfig, config.path)), config.name)
        (actor ? GetBlog).map(_ => config -> actor)
      }).map { blogs =>
        theSender ! BlogsLoaded(blogs)
      }.recover {
        case NonFatal(e) => theSender ! Failure(e)
      }
    }
  }

}
