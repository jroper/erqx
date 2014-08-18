package migration

import java.io.{FileWriter, File}
import scala.xml.{PCData, Elem, XML}
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import java.util.Locale
import au.id.jazzy.erqx.engine.models.{Yaml, BlogPost}

/**
 * Helper for migrating a pebble blog to this blog
 */
object MigratePebbleBlog {

  def migrate(source: File, dest: File, baseUrl: String) = {
    val postFiles = locatePosts(source)

    val posts = postFiles.map(parsePost).flatMap(_.toSeq)

    val to = dest
    val postsDir = new File(dest, "_posts")

    postsDir.mkdirs()

    posts.foreach { post =>
      println("Migrating blog post: " + post._1.toPermalink + "...")

      /*
      println("  Title: " + post._1.title)
      println("  Comments:")
      post._3.foreach { comment =>
        println("    Comment: " + comment.id)
        println("      Author: " + comment.author)

      }
      */

      val body = formatPost(post._1, post._2)
      val postFile = new File(postsDir, post._1.path)
      writeFile(postFile, body)
      println("Wrote: " + postFile)
      println()
    }

    println("Creating WXR...")
    val xwr = generateXwr(posts, baseUrl)
    writeFile(new File(to, "disqus.wxr"), xwr.buildString(true))
    println("Wrote XWR file.")
    println()

  }

  // See http://help.disqus.com/customer/portal/articles/472150
  def generateXwr(posts: List[(BlogPost, String, Seq[Comment])], baseUrl: String): Elem = {
    <rss version="2.0"
         xmlns:content="http://purl.org/rss/1.0/modules/content/"
         xmlns:dsq="http://www.disqus.com/"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         xmlns:wp="http://wordpress.org/export/1.0/">
      <channel>
        {posts.map {
          case (post, body, comments) => generatePostXwr(post, body, comments, baseUrl)
        }}
      </channel>
    </rss>
  }

  def generatePostXwr(post: BlogPost, body: String, comments: Seq[Comment], baseUrl: String): Elem = {
    <item>
      <!-- title of article -->
      <title>{post.title}</title>
      <!-- absolute URI to article -->
      <link>{baseUrl + post.toPermalink}</link>
      <!-- body of the page or post; use cdata; html allowed (though will be formatted to DISQUS specs) -->
      <content:encoded>{PCData(body)}</content:encoded>
      <!-- value used within disqus_identifier; usually internal identifier of article -->
      <dsq:thread_identifier>{post.id}</dsq:thread_identifier>
      <!-- creation date of thread (article), in GMT. Must be YYYY-MM-DD HH:MM:SS 24-hour format. -->
      <wp:post_date_gmt>{WpDateFormat.print(post.date.toDateTime)}</wp:post_date_gmt>
      <!-- open/closed values are acceptable -->
      <wp:comment_status>open</wp:comment_status>

      {comments.map(generateCommentXwr)}
    </item>
  }

  def generateCommentXwr(comment: Comment): Elem = {
    <wp:comment>
      <!-- internal id of comment -->
      <wp:comment_id>{comment.id}</wp:comment_id>
      <!-- author display name -->
      <wp:comment_author>{comment.author}</wp:comment_author>
      <!-- author email address -->
      {comment.email.map { email =>
        <wp:comment_author_email>{email}</wp:comment_author_email>
      }.toSeq}
      <!-- author url, optional -->
      {comment.website.map { website =>
        <wp:comment_author_url>{website}</wp:comment_author_url>
      }.toSeq}
      <!-- author ip address -->
      <wp:comment_author_IP>{comment.ip}</wp:comment_author_IP>
      <!-- comment datetime, in GMT. Must be YYYY-MM-DD HH:MM:SS 24-hour format. -->
      <wp:comment_date_gmt>{WpDateFormat.print(comment.id)}</wp:comment_date_gmt>
      <!-- comment body; use cdata; html allowed (though will be formatted to DISQUS specs) -->
      <wp:comment_content>{PCData(comment.body)}</wp:comment_content>
      <!-- is this comment approved? 0/1 -->
      <wp:comment_approved>{if (comment.approved) 1 else 0}</wp:comment_approved>
      <!-- parent id (match up with wp:comment_id) -->
      {comment.parent.map { parent =>
        <wp:comment_parent>{parent}</wp:comment_parent>
      }.toSeq}
    </wp:comment>
  }

  def locatePosts(source: File): List[File] = {
    // year
    source.listFiles().toList.filter(_.isDirectory).filter(_.getName.matches("\\d{4}")).flatMap { year =>
      // month
      year.listFiles().toList.filter(_.isDirectory).filter(_.getName.matches("\\d{2}")).flatMap { month =>
        // day
        month.listFiles().toList.filter(_.isDirectory).filter(_.getName.matches("\\d{2}")).flatMap { day =>
          // posts
          day.listFiles().toList.filter(_.isFile).filter(_.getName.matches("\\d+\\.xml"))
        }
      }
    }
  }

  def formatPost(post: BlogPost, body: String): String = {
    val props = Map(
      "title" -> ("\"" + post.title + "\""),
      "date" -> ISODateTimeFormat.dateTime.print(post.date)
    ) ++ (
      if (post.tags.isEmpty) Map()
      else Map("tags" -> post.tags.map(_.replace(' ', '+')).mkString(" "))
    )
    val frontMatter = props.map(p => p._1 + ": " + p._2).mkString("---\n", "\n", "\n---\n")

    frontMatter + body
  }
  
  def parsePost(post: File): Option[(BlogPost, String, Seq[Comment])] = {
    val xml = XML.loadFile(post)
    (xml \ "state").text match {
      case "published" =>
        val title = (xml \ "title").text
        val dateStr = (xml \ "date").text
        val timeZone = (xml \ "timeZone").text
        val body = (xml \ "body").text
        val tagsStr = (xml \ "tags").text

        val date = parsePostDate(dateStr, timeZone)
        val permalinkTitle = toPermalinkTitle(title)
        val id = "%04d-%02d-%02d-%s".format(date.year.get, date.monthOfYear.get, date.dayOfMonth.get, permalinkTitle)
        val path = id + ".html"
        val tags = tagsStr.split(" +").map(_.replace('+', ' ').trim()).toSet.filter(_.nonEmpty)

        val post = BlogPost(id, path, title, date, permalinkTitle, "html", tags, Yaml.empty)

        val comments = parseComments(xml)
        Some((post, transformPost(body), comments))

      case _ => None
    }
  }

  case class Comment(id: Long, author: String, email: Option[String], website: Option[String], ip: String,
                     parent: Option[Long], approved: Boolean, body: String)

  def parseComments(blogPostXml: Elem): Seq[Comment] = {
    (blogPostXml \ "comment").flatMap { xml =>
      ((xml \ "state").text match {
        case "approved" => Some(true)
        case "pending" => Some(false)
        case _ => None
      }).map { approved =>
        val date = (xml \ "date").text
        val author = (xml \ "author").text
        val email = (xml \ "email").headOption.map(_.text).filterNot(_.isEmpty)
        val website = (xml \ "website").headOption.map(_.text).filterNot(_.isEmpty)
        val ip = (xml \ "ipAddress").text
        val parent = (xml \ "parent").headOption.map(_.text.toLong)
        val body = (xml \ "body").text

        val id = PebbleDateFormat.parseDateTime(date).getMillis

        Comment(id, author, email, website, ip, parent, approved, fixupComment(body))
      }
        // There are some pending spam comments in the data that have very long author names, filter them out
        .filterNot(_.author.length > 250).toSeq
    }
  }

  def toPermalinkTitle(title: String): String = {
    // copied from Pebble
    title.toLowerCase(Locale.ENGLISH).replaceAll("[\\. ,;/\\\\-]", "_")
      .replaceAll("[^a-z0-9_]", "")
      .replaceAll("_+", "_")
      .replaceAll("^_*", "")
      .replaceAll("_*$", "")
  }

  def parsePostDate(date: String, timeZone: String): DateTime = {
    PebbleDateFormat.parseDateTime(date).toDateTime(DateTimeZone.forID(timeZone))
  }

  def transformPost(post: String) = {
    migrateNewBrushCode(migrateOldBrushCode(renderPebbleWiki(post)))
  }

  /**
   * Some posts are using Pebbles wiki markup.  Render them if so.
   */
  def renderPebbleWiki(post: String): String = RadeoxRenderer.wikify(post)

  val OldBrushCode = """(?s)<pre\s+name="code"\s+class="(?:brush: )?([^":]+)[^"]*"\s*>\n?(.*?)</pre>""".r

  /**
   * Migrate old brush code to google syntax highlighter
   */
  def migrateOldBrushCode(post: String): String = {
    OldBrushCode.replaceAllIn(post, """<pre class="prettyprint"><code class="language-$1">$2</code></pre>""")
  }

  val NewBrushCode = """(?s)<pre\s+class="brush: ([^"]+)"\s*>\n?(.*?)</pre>""".r

  /**
   * Migrate new brush code to google syntax highlighter
   */
  def migrateNewBrushCode(post: String): String = {
    NewBrushCode.replaceAllIn(post, """<pre class="prettyprint"><code class="language-$1">$2</code></pre>""")
  }

  val CommentFixer = "(?s)&lt;(/?(?:(?:div)|(?:span)|(?:p))).*?&gt;".r

  /**
   * Try and fix up comments, by removing escaped spans/divs
   */
  def fixupComment(comment: String): String = {
    CommentFixer.replaceAllIn(comment, "<$1>")
  }

  val PebbleDateFormat = DateTimeFormat.forPattern("dd MMM yyyy HH:mm:ss:SSS Z")
  val WpDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  def writeFile(file: File, content: String) = {
    val writer = new FileWriter(file)
    try {
      writer.write(content)
    } finally {
      writer.close()
    }
  }
}
