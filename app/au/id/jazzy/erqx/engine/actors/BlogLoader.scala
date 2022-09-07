package au.id.jazzy.erqx.engine.actors

import akka.actor.Actor

import scala.concurrent.blocking
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.services.git.{GitBlogRepository, GitDraft, GitRepository}
import org.slf4j.LoggerFactory

object BlogLoader {
  case class ReloadBlog(old: Blog)

  case class LoadBlog(id: String, path: String)
}

/**
 * Actor responsible for loading a blog
 */
class BlogLoader(gitRepository: GitRepository, blogRepository: GitBlogRepository) extends Actor {

  private val log = LoggerFactory.getLogger(getClass)

  import BlogLoader._

  def receive: Receive = {
    case LoadBlog(id, path) =>
      blocking {
        val hash = gitRepository.currentHash
        sender ! blogRepository.loadBlog(id, path, hash, gitRepository.listDrafts())
      }
    case ReloadBlog(old) =>
      blocking {
        gitRepository.fetch()
        hasChanged(old) match {
          case Some((hash, drafts)) =>
            sender ! blogRepository.loadBlog(old.id, old.path, hash, drafts)
          case None =>
            sender ! old
        }
      }
  }

  private def hasChanged(old: Blog): Option[(String, Seq[GitDraft])] = {
    val hash = gitRepository.currentHash
    val drafts = gitRepository.listDrafts()
    if (hash != old.hash) {
      log.info(s"Detected change on git repository for blog ${old.id}, new hash is $hash")
      Some((hash, drafts))
    } else {
      if (drafts.map(_.commitId).toSet != old.drafts.keys.toSet) {
        log.info(s"Detected change in drafts for git repository for blog ${old.id}, new drafts are: ${drafts.map { d => s"${d.name}=${d.commitId}" }.mkString(", ")}")
        Some((hash, drafts))
      } else None
    }
  }
}
