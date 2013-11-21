package actors

import akka.actor.Actor
import actors.BlogLoader.ReloadBlog
import scala.concurrent.blocking
import services.GitRepository
import models.Blog

object BlogLoader {
  case class ReloadBlog(old: Blog)
}

/**
 * Actor responsible for loading a blog
 */
class BlogLoader(gitRepository: GitRepository) extends Actor {

  def receive = {
    case ReloadBlog(old) =>
      blocking {
        gitRepository.fetch
        val hash = gitRepository.currentHash
        if (hash != old.hash) {
          sender ! new Blog(gitRepository.loadBlog(hash).toList, hash, old.path, gitRepository.loadConfig(hash))
        } else {
          sender ! old
        }
      }
  }
}
