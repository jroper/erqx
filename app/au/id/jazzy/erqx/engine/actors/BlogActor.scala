package au.id.jazzy.erqx.engine.actors

import play.api.libs.Crypto
import akka.actor._
import akka.routing._
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
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
  case class FileStream(length: Long, is: InputStream, ec: ExecutionContext)

  case class RenderPage(blog: Blog, page: Page)
}

class BlogActor(config: GitConfig, path: String) extends Actor {

  import BlogLoader._
  import BlogActor._

  private val gitRepository = new GitRepository(config.gitRepo, config.path, config.branch, config.remote)
  private val blogRepository = new GitBlogRepository(gitRepository)

  private val blogLoader = context.actorOf(
    Props(new BlogLoader(gitRepository, blogRepository))
      .withDispatcher("blog-loader-dispatcher"),
    "blogLoader"
  )

  private val fileLoaders = context.actorOf(
    Props(new FileLoader(gitRepository))
      .withRouter(SmallestMailboxPool(nrOfInstances = 10))
      .withDispatcher("file-loader-dispatcher"),
    "fileLoaders")

  private var blog: Blog = _
  private var updateJob: Option[Cancellable] = None

  private def getBlog: Blog = {
    if (blog == null) {
      val hash = gitRepository.currentHash
      blog = blogRepository.loadBlog(config.id, path, hash)
    }
    blog
  }


  override def preStart() = {
    import context.dispatcher

    // If an update interval is configured, then schedule us to update on that interval
    updateJob = config.updateInterval.map { interval =>
      context.system.scheduler.schedule(interval millis, interval millis) {
        self ! Update
      }
    }

    // If a remote is configured, then do an immediate update, this will trigger a fetch, and so potentially trigger
    // an update
    config.remote.foreach { _ =>
      self ! Update
    }
  }

  def receive = {
    case Fetch(key) =>
      if (config.fetchKey.exists(reqKey => Crypto.constantTimeEquals(reqKey, key))) {
        blogLoader ! ReloadBlog(getBlog)
        sender ! FetchAccepted
      } else {
        sender ! FetchRejected
      }
    case Update =>
      blogLoader ! ReloadBlog(getBlog)
    case newBlog: Blog =>
      blog = newBlog

    case GetBlog =>
      try {
        sender ! getBlog
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
