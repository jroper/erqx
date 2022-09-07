package au.id.jazzy.erqx.engine.actors

import akka.actor._
import akka.routing._
import au.id.jazzy.erqx.engine.models._
import akka.actor.Status.Failure
import scala.util.control.NonFatal
import au.id.jazzy.erqx.engine.services.git.{GitBlogRepository, GitRepository}

object BlogActor {
  case class Fetch(key: String)
  case object FetchAccepted
  case object FetchRejected

  case object Update

  case object GetBlog

  case class LoadContent(blog: Blog, file: String)
  case class LoadStream(blog: Blog, file: String)
  case class RenderPost(blog: Blog, post: BlogPost, absoluteUri: Option[String] = None)

  case class RenderPage(blog: Blog, page: Page)
}

class BlogActor(config: GitConfig, path: String, classLoader: ClassLoader) extends Actor {

  import BlogLoader._
  import BlogActor._

  private val gitRepository = new GitRepository(config)
  private val blogRepository = new GitBlogRepository(gitRepository, classLoader)

  private val blogLoader = context.actorOf(
    Props(new BlogLoader(gitRepository, blogRepository))
      .withDispatcher("erqx.blog-loader-dispatcher"),
    "blogLoader"
  )

  private val fileLoaders = context.actorOf(
    Props(new FileLoader(gitRepository))
      .withRouter(SmallestMailboxPool(nrOfInstances = 10))
      .withDispatcher("erqx.file-loader-dispatcher"),
    "fileLoaders")

  // If an update interval is configured, then schedule us to update on that interval
  private val updateJob = config.updateInterval.map { interval =>
    import context.dispatcher
    context.system.scheduler.scheduleAtFixedRate(interval, interval) { () =>
      self ! Update
    }
  }

  override def preStart() = {
    // Load the blog
    blogLoader ! LoadBlog(config.id, path)

    // If a remote is configured, then do an immediate update, this will trigger a fetch, and so potentially trigger
    // an update
    config.remote.foreach { _ =>
      self ! Update
    }
  }

  def receive = pending()

  def pending(requests: List[(ActorRef, Any)] = Nil): Receive = {
    case blog: Blog =>
      context.become(loaded(blog))
      requests.reverse.foreach {
        case (sender, msg) => self.tell(msg, sender)
      }
    case other =>
      context.become(pending(sender() -> other :: requests))
  }

  def loaded(blog: Blog): Receive = {
    case Fetch(key) =>
      if (config.fetchKey.contains(key)) {
        blogLoader ! ReloadBlog(blog)
        sender ! FetchAccepted
      } else {
        sender ! FetchRejected
      }
    case Update =>
      blogLoader ! ReloadBlog(blog)
    case newBlog: Blog =>
      context.become(loaded(newBlog))

    case GetBlog =>
      try {
        sender ! blog
      } catch {
        case NonFatal(e) => sender ! Failure(e)
      }

    case loadContent: LoadContent =>
      fileLoaders.tell(loadContent, sender())
    case loadStream: LoadStream =>
      fileLoaders.tell(loadStream, sender())
    case renderPost: RenderPost =>
      fileLoaders.tell(renderPost, sender())
    case renderPage: RenderPage =>
      fileLoaders.tell(renderPage, sender())
  }

  override def postStop() = {
    gitRepository.close()
    updateJob.foreach(_.cancel())
  }
}
