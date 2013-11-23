package au.id.jazzy.erqx.engine.actors

import akka.actor.Actor
import scala.concurrent.blocking
import play.doc.PlayDoc
import java.io.File
import au.id.jazzy.erqx.engine.services.{GitFileRepository, GitRepository}

/**
 * Actor responsible for loading and rendering files
 */
class FileLoader(gitRepository: GitRepository) extends Actor {

  import BlogActor._

  def receive = {
    case LoadContent(blog, path) =>
      sender ! blocking(gitRepository.loadContent(blog.hash, path))
    case LoadStream(blog, path) =>
      sender ! blocking(gitRepository.loadStream(blog.hash, path).map {
        case (length, is) => FileStream(length, is, context.dispatcher)
      })
    case RenderPost(blog, post, absoluteUri) =>
      val rendered = blocking {
        gitRepository.loadContent(blog.hash, post.path).map { content =>
          // Strip off front matter
          val lines = content.split("\n").dropWhile(_.trim.isEmpty)
          val body = if (lines.headOption.exists(_.trim == "---")) {
            lines.drop(1).dropWhile(_.trim != "---").drop(1).mkString("\n")
          } else content

          post.format match {
            case "md" =>
              val repo = new GitFileRepository(gitRepository, blog.hash, None)
              new PlayDoc(repo, repo, "", "").render(body, None)
            case _ =>
              body
          }
        }
      }
      val uriAdjusted = absoluteUri.flatMap { uri =>
        rendered.map(c => replaceCommonUris(c, uri))
      }.orElse(rendered)

      sender ! uriAdjusted
  }

  private def replaceCommonUris(s: String, uri: String): String = {
    s.replaceAll("href=\"\\./", "href=\"" + uri)
      .replaceAll("href='\\./", "href='" + uri)
      .replaceAll("src=\"\\./", "src=\"" + uri)
      .replaceAll("src='\\./", "src='" + uri)
  }

}
