package au.id.jazzy.erqx.engine.actors

import akka.actor.Actor
import scala.concurrent.blocking
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.git.{GitBlogRepository, GitRepository}
import play.api.Logger

object BlogLoader {
  case class ReloadBlog(old: Blog)
  case class LoadBlog(id: String, path: String)
}

/**
 * Actor responsible for loading a blog
 */
class BlogLoader(gitRepository: GitRepository, blogRepository: GitBlogRepository) extends Actor {

  import BlogLoader._

  def receive = {
    case LoadBlog(id, path) =>
      blocking {
        val hash = gitRepository.currentHash
        sender ! blogRepository.loadBlog(id, path, hash)
      }
    case ReloadBlog(old) =>
      blocking {
        gitRepository.fetch()
        val hash = gitRepository.currentHash
        if (hash != old.hash) {
          Logger.info("Detected change on git repository for blog " + old.id + ", new hash is " + hash)
          sender ! blogRepository.loadBlog(old.id, old.path, hash)
        } else {
          sender ! old
        }
      }
  }
}
