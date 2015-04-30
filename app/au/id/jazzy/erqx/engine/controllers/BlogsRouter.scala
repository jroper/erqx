package au.id.jazzy.erqx.engine.controllers

import javax.inject.{Singleton, Inject}

import au.id.jazzy.erqx.engine.Blogs
import play.api.i18n.MessagesApi
import play.api.routing._
import play.api.routing.sird._
import play.api.mvc._
import play.core.routing.ReverseRouteContext
import play.utils.UriEncoding
import au.id.jazzy.erqx.engine.models._

@Singleton
class BlogsRouter @Inject() (messages: MessagesApi, blogs: Blogs) extends SimpleRouter {

  // We have to keep a reference to the prefix for the reverse routers
  private var prefix = ""

  private val blogRoutes = {
    blogs.blogs.map {
      case (blogConfig, actor) =>
        def blogPath = prefix + blogConfig.path
        new BlogRouter(new BlogController(messages, actor, new BlogReverseRouter(blogPath, prefix)))
          .withPrefix(blogConfig.path).routes
    }.foldLeft(PartialFunction.empty[RequestHeader, Handler])((r1, r2) => r1.orElse(r2))
  }

  val routes = {
    val globalRoutes: PartialFunction[RequestHeader, Handler] = {
      // Global routes
      case GET(p"/_assets/lib/$path*") => controllers.Assets.versioned("/public/lib", path)
    }

    globalRoutes.orElse(blogRoutes)
  }

  override def withPrefix(prefix: String) = {
    this.prefix = if (prefix == "/") "" else prefix
    super.withPrefix(prefix)
  }
}

class BlogRouter(controller: BlogController) extends SimpleRouter {

  def routes = {
    // Index
    case GET((p"/" | p"") ? Page(page)) => controller.index(page)

    // View single blog post
    case GET(p"/${int(year)}<\d{4}>/${int(month)}<\d{2}>/${int(day)}<\d{2}>/$permalink.html") =>
      controller.view(year, month, day, permalink)

    // By date
    case GET(p"/${int(year)}<\d{4}>.html" ? Page(page)) =>
      controller.year(year, page)
    case GET(p"/${int(year)}<\d{4}>/${int(month)}<\d{2}>.html" ? Page(page)) =>
      controller.month(year, month, page)
    case GET(p"/${int(year)}<\d{4}>/${int(month)}<\d{2}>/${int(day)}<\d{2}>.html" ? Page(page)) =>
      controller.day(year, month, day, page)

    // By tag
    case GET(p"/tags/$tag.html" ? Page(page)) => controller.tag(tag, page)

    // Atom feed
    case GET(p"/atom.xml") => controller.atom()

    // Fetch
    case POST(p"/fetch/$key") => controller.fetch(key)

    // Assets
    case GET(p"/$path*") => controller.asset(path)
  }
}

class BlogReverseRouter(path: => String, globalPath: => String) {
  import controllers.Assets

  def index(page: Page = defaultPage): Call = Call("GET", withPaging(s"$path/", page))

  def view(blogPost: BlogPost): Call = Call("GET", path + blogPost.toPermalink)

  def year(year: Int, page: Page = defaultPage) =
    Call("GET", withPaging(s"$path/$year.html", page))
  def month(year: Int, month: Int, page: Page = defaultPage) =
    Call("GET", withPaging(f"$path%s/$year%d/$month%02d.html", page))
  def day(year: Int, month: Int, day: Int, page: Page = defaultPage) =
    Call("GET", withPaging(f"$path%s/$year%d/$month%02d/$day%02d.html", page))

  def tag(tag: String, page: Page = defaultPage) = Call("GET", withPaging(s"$path/tags/${encode(tag)}.html", page))

  def atom() = Call("GET", s"$path/atom.xml")

  def fetch(key: String) = Call("POST", s"$path/fetch/$key")

  def asset(file: String) = Call("GET", s"$path/$file")

  def webJarAsset(file: String) =
    Call("GET", s"$globalPath/_assets/lib/${Assets.Asset.assetPathBindable(
      ReverseRouteContext(Map("path" -> "/public/lib"))
    ).unbind("file", Assets.Asset(file))}")

  private def withPaging(path: String, page: Page) = {
    val qs = (Nil ++
      (if (page.page != 1) Seq("page=" + page.page) else Nil) ++
      (if (page.perPage != 5) Seq("per_page=" + page.perPage) else Nil)).mkString("&")
    if (qs.isEmpty) path else path + "?" + qs
  }

  private def encode(s: String) = UriEncoding.encodePathSegment(s, "UTF-8")

  val defaultPage = Page(1, 5)
}


