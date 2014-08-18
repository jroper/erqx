package au.id.jazzy.erqx.engine.controllers

import scala.xml.{PCData, Elem}
import play.api.mvc.RequestHeader
import org.joda.time.format.ISODateTimeFormat
import au.id.jazzy.erqx.engine.models._

object FeedFormatter {
  def atom(blog: Blog, posts: List[(BlogPost, String)], router: BlogReverseRouter)(implicit req: RequestHeader): Elem = {
    val blogUpdate = ISODateTimeFormat.dateTime.print(blog.posts.head.date)

    <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/elements/1.1/">
      <title>{blog.info.title}</title>
      <link rel="alternate" type="text/html" href={router.index().absoluteURL()} />
      <link rel="self" type="application/atom+xml" href={router.atom().absoluteURL()} />
      {blog.info.subTitle.map { subTitle =>
        <subtitle>{subTitle}</subtitle>
      }.toSeq}
      <id>{router.index().absoluteURL()}</id>
      <rights>{blog.info.author}</rights>
      <updated>{blogUpdate}</updated>
      <dc:creator>{blog.info.author}</dc:creator>
      <dc:date>{blogUpdate}</dc:date>
      <dc:language>{blog.info.language}</dc:language>
      <dc:rights>{blog.info.author}</dc:rights>
      {posts.map {
        case (post, content) =>
          val postDate = ISODateTimeFormat.dateTime.print(post.date)

          <entry>
            <title>{post.title}</title>
            <link rel="alternate" href={router.view(post).absoluteURL()} />
            {post.tags.map { tag =>
              <category term={tag} scheme={router.tag(tag).absoluteURL()} />
            }}
            <author>
              <name>{blog.info.author}</name>
              <uri>{router.index().absoluteURL()}</uri>
            </author>
            <id>{router.view(post).absoluteURL()}</id>
            <updated>{postDate}</updated>
            <published>{postDate}</published>
            <content type="html">{PCData(content)}</content>
            <dc:date>{postDate}</dc:date>
          </entry>
      }}
    </feed>
  }
}
