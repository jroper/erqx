package au.id.jazzy.erqx.engine.controllers

import akka.actor.ActorSelection
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.Future.{successful => sync}
import scala.concurrent.duration._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.MimeTypes
import play.api.i18n.{I18nSupport, MessagesApi}
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.actors.BlogActor._
import java.io.File

class BlogController(val messagesApi: MessagesApi, blogActor: ActorSelection, router: BlogReverseRouter) extends Controller with I18nSupport {

  implicit val defaultTimeout = Timeout(5 seconds)

  def index(page: Page) = BlogAction.async { implicit req =>
    paged(req.blog.posts, page, None)(router.index)
  }

  def year(year: Int, page: Page) = BlogAction.async { implicit req =>
    paged(req.blog.forYear(year).posts, page, Some(messagesApi("posts.by.year", year)))(p => router.year(year, p))
  }

  def month(year: Int, month: Int, page: Page) = BlogAction.async { implicit req =>
    val byMonth = req.blog.forYear(year).forMonth(month)
    paged(byMonth.posts, page, Some(messagesApi("posts.by.month", year, byMonth.name)))(p => router.month(year, month, p))
  }

  def day(year: Int, month: Int, day: Int, page: Page) = BlogAction.async { implicit req =>
    val byMonth = req.blog.forYear(year).forMonth(month)
    val byDay = byMonth.forDay(day)
    paged(byDay.posts, page,
      Some(messagesApi("posts.by.day", year, byMonth.name, day))
    )(p => router.day(year, month, day, p))
  }

  def tag(tag: String, page: Page) = BlogAction.async { implicit req =>
    paged(req.blog.forTag(tag).getOrElse(Nil), page, Some(messagesApi("posts.by.tag", tag)))(p => router.tag(tag, p))
  }

  def view(year: Int, month: Int, day: Int, permalink: String) = BlogAction.async { implicit req =>
    req.blog.forYear(year).forMonth(month).forDay(day).forPermalink(permalink) match {
      case Some(post) =>
        (blogActor ? RenderPost(req.blog, post)).mapTo[Option[String]].map {
          case Some(rendered) =>
            Ok(req.blog.info.theme.blogPost(req.blog, router, post, rendered))
          case None => notFound(req.blog)
        }
      case None => sync(notFound(req.blog))
    }
  }

  def asset(p: String) = BlogAction.async { implicit req =>
    val path = new File("/" + p).getCanonicalPath.drop(1)
    if (path.startsWith("_")) {
      sync(notFound(req.blog))
    } else {
      // Try first for a static asset
      (blogActor ? LoadStream(req.blog, path)).mapTo[Option[FileStream]].flatMap {
        case Some(FileStream(length, is, ec)) =>
          val result = Result(ResponseHeader(OK, Map(
            CONTENT_LENGTH -> length.toString
          )), Enumerator.fromStream(is)(ec))

          sync(MimeTypes.forFileName(path).map(mt => result.withHeaders(CONTENT_TYPE -> mt)).getOrElse(result))

        case None => {
          // Otherwise see if there's a dynamic page
          req.blog.pageForPermalink(path) match {
            case Some(page) =>
              (blogActor ? RenderPage(req.blog, page)).mapTo[Option[String]].map {
                case Some(rendered) =>
                  Ok(req.blog.info.theme.page(req.blog, router, page, rendered))
                case None => notFound(req.blog)
              }
            case None =>
              sync(notFound(req.blog))
          }
        }
      }
    }
  }

  def paged[A](allPosts: List[BlogPost], p: Page, title: Option[String])
           (route: (Page) => Call)(implicit req: BlogRequest[A]) = {
    val page = if (p.page < 1) 1 else p.page
    val perPage = if (p.perPage < 1) 1 else if (p.perPage > 10) 10 else p.perPage

    val zeroBasedPage = page - 1

    val posts = allPosts.drop(zeroBasedPage * perPage).take(perPage)

    val lastPage = allPosts.size / perPage
    val previous = if (page > 1) Some(route(Page(page - 1, perPage))) else None
    val next = if (page < lastPage) Some(route(Page(page + 1, perPage))) else None

    // Load blog posts
    Future.sequence(posts.map { post =>
      (blogActor ? RenderPost(req.blog, post)).mapTo[Option[String]].map(_.map(post -> _))
    }).map { loaded =>
      Ok(req.blog.info.theme.blogPosts(req.blog, router, title, loaded.flatMap(_.toSeq), previous, next))
    }
  }

  def atom = BlogAction.async { implicit req =>
    val posts = req.blog.posts.take(5)
    val absoluteUri = router.index().absoluteURL()
    Future.sequence(posts.map { post =>
      (blogActor ? RenderPost(req.blog, post, Some(absoluteUri))).mapTo[Option[String]].map(_.map(post -> _))
    }).map { loaded =>
      Ok(FeedFormatter.atom(req.blog, loaded.flatMap(_.toSeq), router))
    }
  }

  def fetch(key: String) = Action.async { implicit req =>
    (blogActor ? Fetch(key)) map {
      case FetchAccepted => Ok
      case FetchRejected => Forbidden
    }
  }

  def notFound(blog: Blog)(implicit req: RequestHeader) = NotFound(blog.info.theme.notFound(blog, router))

  /**
   * Action builder for blog requests. Loads the current blog, as well as handles etag caching headers
   */
  object BlogAction extends ActionBuilder[BlogRequest] {

    def invokeBlock[A](request: Request[A], block: (BlogRequest[A]) => Future[Result]) = {
      (blogActor ? GetBlog).mapTo[Blog].flatMap { blog =>
        // etag - take 7 characters of the blog hash and 7 characters of the theme hash. So if either the theme
        // changes, or the blog changes, everything served by the blog will expire.
        val etag = blog.hash.take(7) + blog.info.theme.hash.take(7)
        if (request.headers.get(IF_NONE_MATCH).exists(_ == etag)) {
          sync(NotModified)
        } else {
          block(new BlogRequest(request, blog)).map(_.withHeaders(ETAG -> etag))
        }
      }
    }
  }
}

object BlogController {
  val startTime = System.currentTimeMillis()
}

class BlogRequest[A](request: Request[A], val blog: Blog) extends WrappedRequest[A](request)

case class Page(page: Int, perPage: Int)

object Page {
  import play.api.routing.sird._
  def unapply(req: RequestHeader): Option[Page] = {
    req.queryString match {
      case q_o"page=${int(page)}" & q_o"page=${int(perPage)}" =>
        Some(Page(page.getOrElse(1), perPage.getOrElse(5)))
      case _ => None
    }
  }
}