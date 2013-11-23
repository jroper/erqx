package au.id.jazzy.erqx.engine.actors

import akka.actor._
import akka.routing._
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.GitRepository

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
}

class BlogActor(config: GitConfig, path: String) extends Actor {

  import BlogLoader._
  import BlogActor._

  private val gitRepository = new GitRepository(config.gitRepo, config.path, config.branch, config.remote)

  private val blogLoader = context.actorOf(
    Props(new BlogLoader(gitRepository))
      .withDispatcher("blog-loader-dispatcher"),
    "blogLoader"
  )

  private val fileLoaders = context.actorOf(
    Props(new FileLoader(gitRepository))
      .withRouter(SmallestMailboxRouter(nrOfInstances = 10))
      .withDispatcher("file-loader-dispatcher"),
    "fileLoaders")

  private var blog: Blog = _

  private def getBlog: Blog = {
    if (blog == null) {
      val hash = gitRepository.currentHash
      blog = new Blog(gitRepository.loadBlog(hash).toList, hash, path, gitRepository.loadConfig(hash))
    }
    blog
  }


  override def preStart() = {
    import context.dispatcher

    config.updateInterval.foreach { interval =>
      context.system.scheduler.schedule(interval millis, interval millis) {
        self ! Update
      }
    }
  }

  def receive = {
    case Fetch(key) =>
      if (config.fetchKey.exists(_ == key)) {
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

      sender ! getBlog

    case loadContent: LoadContent =>
      fileLoaders.tell(loadContent, sender)
    case loadStream: LoadStream =>
      fileLoaders.tell(loadStream, sender)
    case renderPost: RenderPost =>
      fileLoaders.tell(renderPost, sender)
  }

  override def postStop() = gitRepository.close
}
