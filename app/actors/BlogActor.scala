package actors

import akka.actor._
import akka.routing._
import models.{GitConfig, BlogPost, Blog}
import services.GitRepository
import java.io.{InputStream, File}
import scala.concurrent.ExecutionContext

object BlogActor {
  case object PingRepository

  case object GetBlog

  case class LoadContent(blog: Blog, file: String)
  case class LoadStream(blog: Blog, file: String)
  case class RenderPost(blog: Blog, post: BlogPost)
  case class FileStream(length: Long, is: InputStream, ec: ExecutionContext)
}

class BlogActor(config: GitConfig, path: String) extends Actor {

  import BlogLoader._
  import BlogActor._

  private val gitRepository = new GitRepository(config.gitRepo, config.branch, config.remote)
  private val blogLoader = context.actorOf(Props(new BlogLoader(gitRepository)), "blogLoader")
  private val fileLoaders = context.actorOf(Props(new FileLoader(gitRepository)).withRouter(FromConfig()),
    "fileLoaders")

  private var blog: Blog = {
    val hash = gitRepository.currentHash
    new Blog(gitRepository.loadBlog(hash).toList, hash, path)
  }

  def receive = {
    case PingRepository =>
      blogLoader ! ReloadBlog(blog)
    case newBlog: Blog =>
      blog = newBlog

    case GetBlog =>
      sender ! blog

    case loadContent: LoadContent =>
      fileLoaders.tell(loadContent, sender)
    case loadStream: LoadStream =>
      fileLoaders.tell(loadStream, sender)
    case renderPost: RenderPost =>
      fileLoaders.tell(renderPost, sender)
  }

  override def postStop() = gitRepository.close
}
