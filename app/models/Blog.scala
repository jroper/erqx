package models

import scala.collection.SortedMap
import controllers.BlogReverseRouter
import play.api.mvc.{Call, RequestHeader}
import play.api.templates.Html

/**
 * Information about a blog
 */
case class BlogInfo(title: String,
                    subTitle: Option[String],
                    author: String,
                    language: String,
                    description: Option[String] = None,
                    theme: BlogTheme = DefaultTheme,
                    properties: Yaml = Yaml.empty)

trait BlogTheme {
  val name: String

  /**
   * The main template
   */
  def main(blog: Blog, router: BlogReverseRouter, title: Option[String])(content: Html)(implicit req: RequestHeader): Html =
    views.html.themes.jazzy.main(blog, router, title)(content)

  /**
   * The template for rendering a blog post
   */
  def blogPost(blog: Blog, router: BlogReverseRouter,
               post: BlogPost, content: String)(implicit req: RequestHeader): Html =
    views.html.themes.jazzy.blogPost(blog, router, post, content)

  /**
   * The template for rendering a list of blog posts
   */
  def blogPosts(blog: Blog, router: BlogReverseRouter, title: Option[String],
               posts: List[(BlogPost, String)], previous: Option[Call], next: Option[Call])(implicit req: RequestHeader): Html =
    views.html.themes.jazzy.blogPosts(blog, router, title, posts, previous, next)

  /**
   * The template for rendering the not found page
   */
  def notFound(blog: Blog, router: BlogReverseRouter)(implicit req: RequestHeader): Html = {
    views.html.themes.jazzy.pageNotFound(blog, router)
  }
}

/**
 * The default theme
 */
object DefaultTheme extends BlogTheme {
  val name = "jazzy"
}

/**
 * A blog
 *
 * @param blogPosts The blog posts
 * @param hash The current hash of the repository at which point these blog posts were loaded from
 * @param path The path of the blog.  Does not end with "/", may be blank.
 */
final class Blog(blogPosts: List[BlogPost], val hash: String = "", val path: String, val info: BlogInfo) {

  /**
   * Blog posts are always listed in reverse chronological order, so we sort them and then reverse them
   */
  private val sorted: List[BlogPost] = blogPosts.sorted.reverse

  /**
   * Blog posts by year, month then day
   */
  val years: List[Year] = {
    // Because this is based on sorted, the posts within the years/months/days should also be sorted
    // because groupBy is stable
    sorted.groupBy(_.date.year.get).map { byYear =>
      val (year, yearPosts) = byYear
      
      // Create months map
      val months = SortedMap.empty[Int, Month] ++ yearPosts.groupBy(_.date.monthOfYear.get).map { byMonth =>
        val (month, monthPosts) = byMonth
        
        // Create days map
        val days = SortedMap.empty[Int, Day] ++ monthPosts.groupBy(_.date.dayOfMonth.get).map { byDay =>
          val (day, dayPosts) = byDay
          
          day -> Day(year, month, day, dayPosts)
        }
      
        month -> Month(year, month, days, monthPosts)
      }

      Year(year, months, yearPosts)
    }.toList.sorted
  }

  /**
   * Get the blog posts for the given year
   */
  def forYear(year: Int): Year = years.find(_.year == year).getOrElse(Year(year, SortedMap.empty, Nil))

  /**
   * Blog posts by tag
   */
  val tags: Map[String, List[BlogPost]] =
    sorted.flatMap(post => post.tags.map(_ -> post))
      .groupBy(_._1)
      .mapValues(_.map(_._2))

  /**
   * Tag cloud, a list of tag names, number of posts, and tag weights, which is a number from 1 to 10
   */
  val tagCloud: List[(String, Int, Int)] = {
    val rankFactor = Math.max(1.0, 10.0 / tags.map(_._2.size).fold(0)(Math.max))

    tags.map {
      case (tag, posts) => (tag, posts.size, Math.ceil(rankFactor * posts.size).toInt)
    }.toList.sortBy(_._1)
  }

  /**
   * Get the blog posts for the given tag
   */
  def forTag(tag: String): Option[List[BlogPost]] = tags.get(tag)

  /**
   * Get the posts, sorted in reverse chronological order
   */
  def posts: List[BlogPost] = sorted

}
