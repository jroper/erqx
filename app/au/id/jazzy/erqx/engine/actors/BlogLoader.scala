package au.id.jazzy.erqx.engine.actors

import akka.actor.Actor
import scala.concurrent.blocking
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.GitRepository

object BlogLoader {
  case class ReloadBlog(old: Blog)
}

/**
 * Actor responsible for loading a blog
 */
class BlogLoader(gitRepository: GitRepository) extends Actor {

  import BlogLoader._

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
