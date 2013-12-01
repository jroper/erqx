package au.id.jazzy.erqx.engine.controllers

import play.core.Router.Routes
import play.api.mvc._
import akka.actor.ActorRef
import play.utils.UriEncoding
import au.id.jazzy.erqx.engine.models._

trait BlogsRouter extends Routes {

  private var path: String = ""
  private var blogs: List[BlogRouter] = Nil
  // The routes function for going through each blog.  This is mainly just an optimisation, it could be calculated
  // each time
  private var blogsRoutes: PartialFunction[RequestHeader, Handler] = PartialFunction.empty

  def startBlogs(blogs: List[(BlogConfig, ActorRef)]) = {
    this.blogs = blogs.map {
      case (blogConfig, actor) =>
        
        val blogPath = path + blogConfig.path

        new BlogRouter(
          new BlogController(actor,
            new BlogReverseRouter(blogPath, path)),
          blogPath)
    }

    blogsRoutes = this.blogs.map(_.routes).reduceLeft((r1, r2) => r1.orElse(r2))
  }

  def documentation = Nil

  def routes = Function.unlift[RequestHeader, Handler]({ req =>
    import BlogPaths._

    if (req.method == "GET" && req.path.startsWith(path)) {

      val subPath = req.path.drop(path.length)

      subPath match {
        case AssetsPattern(p) => Some(controllers.Assets.at("/public/au/id/jazzy/erqx/themes", p))
        case WebJarAssetsPattern(p) => Some(controllers.WebJarAssets.at(p))
        case _ => None
      }
    } else None

  }).orElse(blogsRoutes)

  def setPrefix(prefix: String) = {
    if (prefix.endsWith("/")) {
      path = prefix.dropRight(1)
    } else {
      path = prefix
    }
  }

  def prefix = path
}

object BlogsRouter extends BlogsRouter

object BlogPaths {
  val PostPattern = """/(\d{4})/(\d{2})/(\d{2})/([^/]+)\.html""".r

  val YearPattern = """/(\d{4})\.html""".r
  val MonthPattern = """/(\d{4})/(\d{2})\.html""".r
  val DayPattern = """/(\d{4})/(\d{2})/(\d{2})\.html""".r

  val YearLink = "/%d.html"
  val MonthLink = "/%d/%02d.html"
  val DayLink = "/%d/%02d/%02d.html"

  val TagPattern = """/tags/([^/]+)\.html""".r
  val TagLink = "/tags/%s.html"

  val FetchPattern = """/fetch/([^/]+)""".r
  val FetchLink = "/fetch/%s"

  val AssetsPattern = "/_assets/(.*)".r
  val AssetsLink = "/_assets/%s"

  val WebJarAssetsPattern = "/_webjars/(.*)".r
  val WebJarAssetsLink = "/_webjars/%s"

  object ToInt {
    def unapply(s: String): Option[Int] = try {
      Some(s.toInt)
    } catch {
      case e: NumberFormatException => None
    }
  }

  object Decode {
    def unapply(s: String) = Some(UriEncoding.decodePathSegment(s, "UTF-8"))
  }

  def getPage(implicit req: RequestHeader): Page = {
    Page(
      req.getQueryString("page") match {
        case Some(ToInt(p)) => p
        case _ => 1
      },
      req.getQueryString("per_page") match {
        case Some(ToInt(p)) => p
        case _ => 5
      }
    )
  }

}

class BlogRouter(controller: BlogController, path: String) extends Routes {

  import BlogPaths._

  def documentation = Nil

  def routes = Function.unlift { implicit req =>
    // Don't match more than we need to
    if (req.path.startsWith(prefix) || req.path == path) {

      // If it's a HEAD request, do a GET instead
      val isHead = req.method == "HEAD"
      val method = if (isHead) "GET" else req.method

      val subPath = req.path.drop(path.length)

      val action = (method, subPath) match {
        // Index
        case ("GET", "/" | "") =>
          Some(controller.index(getPage))

        // View single blog post
        case ("GET", PostPattern(ToInt(year), ToInt(month), ToInt(day), Decode(permalink))) =>
          Some(controller.view(year, month, day, permalink))

        // By date
        case ("GET", YearPattern(ToInt(year))) =>
          Some(controller.year(year, getPage))
        case ("GET", MonthPattern(ToInt(year), ToInt(month))) =>
          Some(controller.month(year, month, getPage))
        case ("GET", DayPattern(ToInt(year), ToInt(month), ToInt(day))) =>
          Some(controller.day(year, month, day, getPage))

        // By tag
        case ("GET", TagPattern(Decode(tag))) =>
          Some(controller.tag(tag, getPage))

        // Atom feed
        case ("GET", "/atom.xml") =>
          Some(controller.atom())

        // Fetch
        case ("POST", FetchPattern(Decode(key))) =>
          Some(controller.fetch(key))

        // Assets
        case ("GET", path) =>
          Some(controller.asset(path.drop(1)))

        case _ => None
      }

      // Wrap in the head action if it was a head request
      if (isHead) {
        action.map(new HeadAction(_))
      } else {
        action
      }
    } else {
      None
    }

  }

  def setPrefix(prefix: String) = {}

  val prefix = path + "/"
}

class BlogReverseRouter(path: String, globalPath: String) {
  import BlogPaths._

  def index(page: Page = defaultPage): Call = Call("GET", withPaging(path + "/", page))

  def view(blogPost: BlogPost): Call = Call("GET", path + blogPost.toPermalink)

  def year(year: Int, page: Page = defaultPage) =
    Call("GET", withPaging(path + YearLink.format(year), page))
  def month(year: Int, month: Int, page: Page = defaultPage) =
    Call("GET", withPaging(path + MonthLink.format(year, month), page))
  def day(year: Int, month: Int, day: Int, page: Page = defaultPage) =
    Call("GET", withPaging(path + DayLink.format(year, month, day), page))

  def tag(tag: String, page: Page = defaultPage) = Call("GET", withPaging(path + TagLink.format(encode(tag)), page))

  def atom() = Call("GET", path + "/atom.xml")

  def fetch(key: String) = Call("POST", path + FetchLink.format(key))

  def asset(file: String) = Call("GET", path + "/" + file)

  def globalAsset(file: String) = Call("GET", globalPath + AssetsLink.format(file))

  def webJarAsset(file: String) = Call("GET", globalPath + WebJarAssetsLink.format(file))

  private def withPaging(path: String, page: Page) = {
    val qs = (Nil ++
      (if (page.page != 1) Seq("page=" + page.page) else Nil) ++
      (if (page.perPage != 5) Seq("per_page=" + page.perPage) else Nil)).mkString("&")
    if (qs.isEmpty) path else path + "?" + qs
  }

  private def encode(s: String) = UriEncoding.encodePathSegment(s, "UTF-8")

  val defaultPage = Page(1, 5)
}


