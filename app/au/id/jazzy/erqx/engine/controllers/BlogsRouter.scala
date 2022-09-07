package au.id.jazzy.erqx.engine.controllers

import javax.inject.{Inject, Singleton}
import au.id.jazzy.erqx.engine.Blogs
import au.id.jazzy.erqx.engine.models.{BlogConfig, BlogPost, ErqxConfig}
import play.api.routing._
import play.api.routing.sird._
import play.api.mvc._
import play.utils.UriEncoding
import controllers.{Assets, AssetsFinder}

@Singleton
class BlogsRouter @Inject() (components: ControllerComponents, blogs: Blogs, assets: Assets, assetsFinder: AssetsFinder,
  erqxConfig: ErqxConfig) extends SimpleRouter {

  // We have to keep a reference to the prefix for the reverse routers
  private var prefix = ""

  private val blogRouters: Seq[(BlogConfig, Router, BlogReverseRouter)] = {
    blogs.blogs.map {
      case (blogConfig, actor) =>
        def blogPath = prefix + blogConfig.path
        val reverseRouter = new BlogReverseRouter(assetsFinder, blogPath, prefix)
        val router = new BlogRouter(new BlogController(components, actor, reverseRouter, blogs.blogRequestCache, erqxConfig.serverPush))
          .withPrefix(blogConfig.path)
        (blogConfig, router, reverseRouter)
    }
  }

  private val blogReverseRouters: Map[String, BlogReverseRouter] = {
    blogRouters.map {
      case (config, _, reverseRouter) => config.name -> reverseRouter
    }.toMap
  }

  /** Get the reverse router for the blog with the given name. */
  def reverseRouterFor(name: String): Option[BlogReverseRouter] =
    blogReverseRouters.get(name)

  private val blogRoutes = blogRouters.map(_._2.routes)
    .foldLeft(PartialFunction.empty[RequestHeader, Handler])((r1, r2) => r1.orElse(r2))

  val routes = {
    val globalRoutes: PartialFunction[RequestHeader, Handler] = {
      // Global routes
      case GET(p"/_assets/lib/$path*") => assets.versioned("/public/lib", path)
    }

    globalRoutes.orElse(blogRoutes)
  }

  override def withPrefix(prefix: String) = {
    this.prefix = if (prefix == "/") "" else prefix
    super.withPrefix(prefix)
  }
}

class BlogRouter(controller: BlogController) extends SimpleRouter { self =>

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

    // Drafts
    case GET(p"/drafts/$commitId" ? Page(page)) =>
      controller.withDraftBlog(commitId)(_.index(page))

    case req@GET(p"/drafts/$commitId/$_*") =>
      controller.withDraftBlog(commitId) { draftController =>
        new BlogRouter(draftController)
          .withPrefix(s"/drafts/$commitId")
          .routes(req).asInstanceOf[Action[Unit]]
      }

    // Assets
    case GET(p"/$path*") => controller.asset(path)
  }

  // Copied from Plays SimpleRouter to fix allowing the router to handle paths that are exactly equal to the prefix
  override def withPrefix(prefix: String): Router = {
    if (prefix == "/") {
      self
    } else {
      new Router {
        def routes = {
          val p = if (prefix.endsWith("/")) prefix else prefix + "/"
          val prefixed: PartialFunction[RequestHeader, RequestHeader] = {
            case rh: RequestHeader if rh.path.startsWith(p) || rh.path.equals(prefix) =>
              rh.withTarget(rh.target.withPath(rh.path.drop(p.length - 1)))
          }
          Function.unlift(prefixed.lift.andThen(_.flatMap(self.routes.lift)))
        }
        def withPrefix(prefix: String) = self.withPrefix(prefix)
        def documentation = self.documentation
      }
    }
  }
}

class BlogReverseRouter(assetsFinder: AssetsFinder, path: => String, globalPath: => String) {
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
    Call("GET", s"$globalPath/_assets/lib/${assetsFinder.findAssetPath("/public/lib", "/public/lib/" + file)}")

  def draft(commitId: String): BlogReverseRouter = {
    new BlogReverseRouter(assetsFinder, s"$path/drafts/$commitId", globalPath)
  }

  private def withPaging(path: String, page: Page) = {
    val qs = (Nil ++
      (if (page.page != Page.DefaultPage) Seq("page=" + page.page) else Nil) ++
      (if (page.perPage != Page.DefaultPageSize) Seq("per_page=" + page.perPage) else Nil)).mkString("&")
    if (qs.isEmpty) path else path + "?" + qs
  }

  private def encode(s: String) = UriEncoding.encodePathSegment(s, "UTF-8")

  val defaultPage = Page(1, 5)
}


