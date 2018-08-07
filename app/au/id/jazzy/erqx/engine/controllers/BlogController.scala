package au.id.jazzy.erqx.engine.controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => sync}
import scala.concurrent.duration._
import play.api.mvc._
import au.id.jazzy.erqx.engine.models._
import au.id.jazzy.erqx.engine.actors.BlogActor._
import java.io.File

import au.id.jazzy.erqx.engine.actors.BlogRequestCache
import play.api.http.HttpEntity
import play.api.i18n.{I18nSupport, Lang, Messages}

class BlogController(components: ControllerComponents, blogActor: ActorRef, router: BlogReverseRouter,
  blogRequestCache: ActorRef, serverPush: ServerPush)

  extends AbstractController(components) with I18nSupport {

  private implicit val defaultTimeout: Timeout = Timeout(5.seconds)
  private implicit def langFromMessages(implicit messages: Messages): Lang = messages.lang
  private implicit val ec: ExecutionContext = components.executionContext

  def messages(key: String, args: Any*)(implicit rh: RequestHeader) = messagesApi.preferred(rh).apply(key, args: _*)

  def index(page: Page): Action[Unit] = BlogActionWithPushes.async { implicit req =>
    paged(req.blog.posts, page, None)(router.index)
  }

  def year(year: Int, page: Page): Action[Unit] = BlogActionWithPushes.async { implicit req =>
    paged(req.blog.forYear(year).posts, page, Some(messages("posts.by.year", year)))(p => router.year(year, p))
  }

  def month(year: Int, month: Int, page: Page): Action[Unit] = BlogActionWithPushes.async { implicit req =>
    val byMonth = req.blog.forYear(year).forMonth(month)
    paged(byMonth.posts, page, Some(messages("posts.by.month", year, byMonth.name)))(p => router.month(year, month, p))
  }

  def day(year: Int, month: Int, day: Int, page: Page): Action[Unit] = BlogActionWithPushes.async { implicit req =>
    val byMonth = req.blog.forYear(year).forMonth(month)
    val byDay = byMonth.forDay(day)
    paged(byDay.posts, page,
      Some(messages("posts.by.day", year, byMonth.name, day))
    )(p => router.day(year, month, day, p))
  }

  def tag(tag: String, page: Page): Action[Unit] = BlogActionWithPushes.async { implicit req =>
    paged(req.blog.forTag(tag).getOrElse(Nil), page, Some(messages("posts.by.tag", tag)))(p => router.tag(tag, p))
  }

  def view(year: Int, month: Int, day: Int, permalink: String): Action[Unit] = BlogActionWithPushes.async { implicit req =>
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

  def asset(p: String): Action[Unit] = BlogAction.async { implicit req =>
    val path = new File("/" + p).getCanonicalPath.drop(1)
    if (path.startsWith("_")) {
      sync(notFound(req.blog))
    } else {
      // Try first for a static asset
      (blogActor ? LoadStream(req.blog, path)).mapTo[Option[HttpEntity]].flatMap {
        case Some(entity) =>
          val withContentType = components.fileMimeTypes.forFileName(path).fold(entity)(entity.as)
          val result = Ok.sendEntity(withContentType)

          val withCacheControl = req.blog.info.assetsExpiry match {
            case Some(Duration.Inf) => result.withHeaders(
              CACHE_CONTROL -> "public, immutable"
            )
            case Some(duration) => result.withHeaders(
              CACHE_CONTROL -> s"public, max-age=${duration.toSeconds}"
            )
            case None => result
          }

          sync(withCacheControl)

        case None =>
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

  def paged[A](allPosts: List[BlogPost], p: Page, title: Option[String])
           (route: Page => Call)(implicit req: BlogRequest[A]): Future[Result] = {
    val page = p.page.max(1)
    val perPage = p.perPage.max(1).min(Page.MaxPageSize)

    val zeroBasedPage = page - 1

    val pageStart = zeroBasedPage * perPage
    val posts = allPosts.slice(pageStart, pageStart + perPage)

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

  def atom: Action[Unit] = BlogAction.async { implicit req =>
    val posts = req.blog.posts.take(5)
    val absoluteUri = router.index().absoluteURL()
    Future.sequence(posts.map { post =>
      (blogActor ? RenderPost(req.blog, post, Some(absoluteUri))).mapTo[Option[String]].map(_.map(post -> _))
    }).map { loaded =>
      Ok(FeedFormatter.atom(req.blog, loaded.flatMap(_.toSeq), router))
    }
  }

  def fetch(key: String): Action[AnyContent] = Action.async { implicit req =>
    (blogActor ? Fetch(key)) map {
      case FetchAccepted => Ok
      case FetchRejected => Forbidden
    }
  }

  def notFound(blog: Blog)(implicit req: RequestHeader) = NotFound(blog.info.theme.notFound(blog, router))

  /**
   * Action builder for blog requests. Loads the current blog, as well as handles etag caching headers
   */
  private object BlogAction extends ActionBuilder[BlogRequest, Unit] {

    override val parser: BodyParser[Unit] = components.parsers.empty
    override protected def executionContext: ExecutionContext = components.executionContext

    override def invokeBlock[A](request: Request[A], block: BlogRequest[A] => Future[Result]): Future[Result] = {
      (blogActor ? GetBlog).mapTo[Blog].flatMap { blog =>
        (blogRequestCache ? BlogRequestCache.ExecuteRequest(
          new BlogRequest(request, blog), block
        )).mapTo[Result]
      }
    }
  }

  /**
    * Adds push support
    */
  private object WithPushes extends ActionFunction[BlogRequest, BlogRequest] {

    override protected def executionContext: ExecutionContext = components.executionContext

    override def invokeBlock[A](request: BlogRequest[A], block: BlogRequest[A] => Future[Result]): Future[Result] = {
      block(request).map { result =>
        request.cookies.get(serverPush.cookie).filter(_.value == request.blog.info.theme.hash) match {
          case None =>
            val preload = request.blog.info.theme.pushAssets(request.blog, router)
              .map(asset => s"<${asset.asset.url}>; rel=preload; as=${asset.as}")
              .mkString(", ")
            result.withHeaders(LINK -> preload)
              .withCookies(Cookie(serverPush.cookie, request.blog.info.theme.hash))
          case Some(_) =>
            result
        }
      }
    }

  }

  private val BlogActionWithPushes = serverPush.method match {
    case ServerPushMethod.Link => BlogAction.andThen(WithPushes)
    case ServerPushMethod.None => BlogAction
  }

}

object BlogController {
  val startTime: Long = System.currentTimeMillis()
}

class BlogRequest[A](request: Request[A], val blog: Blog) extends WrappedRequest[A](request)

case class Page(page: Int, perPage: Int)

object Page {
  import play.api.routing.sird._

  val DefaultPage = 1
  val DefaultPageSize = 5
  val MaxPageSize = 10
  val Default = Page(DefaultPage, DefaultPageSize)

  def unapply(req: RequestHeader): Option[Page] = {
    req.queryString match {
      case q_o"page=${int(page)}" & q_o"per_page=${int(perPage)}" =>
        Some(Page(page.getOrElse(Page.DefaultPage), perPage.getOrElse(Page.DefaultPageSize)))
      // We only get here if the page or per_page weren't integers, in which case, ignore and default
      // to the default page
      case _ => Some(Default)
    }
  }
}