package au.id.jazzy.erqx.engine.models

import java.time.ZonedDateTime

import play.utils.UriEncoding

/**
 * A blog post
 *
 * @param id The id of the blog post
 * @param path The path of the file that contains the blog post
 * @param title The title of the blog post
 * @param date The date the post is published
 * @param permalinkTitle The title used in the permalink of the blog post
 * @param tags The tags associated with the blog post
 */
final case class BlogPost(id: String, path: String, title: String, date: ZonedDateTime, permalinkTitle: String,
                          format: String, tags: Set[String], properties: Yaml = Yaml.empty) {
  def toPermalink = "/%04d/%02d/%02d/%s.html".format(date.getYear, date.getMonthValue, date.getDayOfMonth,
    UriEncoding.encodePathSegment(permalinkTitle, "UTF-8"))
}

object BlogPost {
  implicit val ordering = Ordering.by((post: BlogPost) => post.date.toEpochSecond)
}
