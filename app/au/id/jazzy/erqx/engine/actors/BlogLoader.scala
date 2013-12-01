package au.id.jazzy.erqx.engine.actors

import akka.actor.Actor
import scala.concurrent.blocking
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.git.{GitBlogRepository, GitRepository}
import play.api.Logger

object BlogLoader {
  case class ReloadBlog(old: Blog)
}

/**
 * Actor responsible for loading a blog
 */
class BlogLoader(gitRepository: GitRepository, blogRepository: GitBlogRepository) extends Actor {

  import BlogLoader._

  def receive = {
    case ReloadBlog(old) =>
      blocking {
        gitRepository.fetch
        val hash = gitRepository.currentHash
        if (hash != old.hash) {
          Logger.info("Detected change on git repository for blog " + old.id + ", new hash is " + hash)
          sender ! new Blog(old.id, blogRepository.loadBlog(hash).toList, hash, old.path, blogRepository.loadConfig(hash))
        } else {
          sender ! old
        }
      }
  }
}
