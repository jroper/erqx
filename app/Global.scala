import actors.BlogsActor
import actors.BlogsActor._
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import controllers.BlogsRouter
import play.api._
import play.api.libs.concurrent.Akka
import scala.concurrent.duration._
import scala.concurrent.Await

object Global extends GlobalSettings{
  override def onStart(app: Application) = {
    val system = Akka.system(app)

    implicit val timeout = Timeout(1 minute)
    val BlogsLoaded(blogs) = Await.result((system.actorOf(Props[BlogsActor], "blogs") ? LoadBlogs).mapTo[BlogsLoaded], 1 minute)
    BlogsRouter.startBlogs(blogs)
  }
}
