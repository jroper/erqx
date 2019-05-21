package au.id.jazzy.erqx.engine.services

import java.io.InputStream
import java.time.{ZoneId, ZonedDateTime}

import scala.io.Source
import au.id.jazzy.erqx.engine.models._
import org.slf4j.LoggerFactory

object MetaDataParser {

  private val log = LoggerFactory.getLogger(getClass)

  private val NameFormatExtractor = """(.+)\.([^\.]+)""".r
  private val NameAttributeExtractor = """(\d{4})-(\d{1,2})-(\d{1,2})-(.+)""".r
  private val DateTimeParser = """(\d{4})/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{2})""".r
  private val DateParser = """(\d{4})/(\d{1,2})/(\d{1,2})""".r
  private object ToInt {
    def unapply(s: String): Option[Int] = try Some(s.toInt) catch { case _: NumberFormatException => None }
  }
  private implicit class IteratorOps[T](i: Iterator[T]) {
    def nextOption = if (i.hasNext) Option(i.next()) else None
  }

  /**
   * Parse the front matter from a blog post.
   *
   * @param stream The stream to parse
   * @param path The path of the blog post
   * @param name The name of the blog post
   * @return
   */
  def parsePostFrontMatter(stream: InputStream, path: String, name: String, timezone: ZoneId): BlogPost = {
    val yaml = extractFrontMatter(stream, timezone)

    val props = Yaml(yaml.map -- Seq("title", "id", "date", "tags"))
    val fTitle = yaml.getString("title")
    val fId = yaml.getString("id").map(_.toString)
    val fDate = yaml.getDate("date")
    val tags = yaml.getString("tags").toSeq.flatMap(_.split(" +").map(_.replace('+', ' ')))

    // Extract id and format from the name
    val (nId, format) = name match {
      case NameFormatExtractor(id, format) => (id, format)
      case _ =>
        log.warn("Could not extract blog post format from name: {}", name)
        (name, "html")
    }

    // Extract attributes from the name
    val (nTitle, nDate, permalinkTitle) = nId match {
      case NameAttributeExtractor(ToInt(year), ToInt(month), ToInt(day), title) =>
        (
          Some(title.replace('_', ' ')),
          Some(ZonedDateTime.of(year, month, day, 0, 0, 0, 0, timezone)),
          title
        )
      case _ => 
        log.warn("Blog post file name not extractable: {}", name)
        (None, None, nId)
    }
    
    val id = fId getOrElse nId
    val title = fTitle orElse nTitle getOrElse id
    val date = fDate orElse nDate getOrElse ZonedDateTime.now()
    
    BlogPost(id, path, title, date, permalinkTitle, format, tags.toSet, props)
  }

  /**
   * Parse the front matter from a page.
   *
   * @param stream The stream to parse
   * @param path The path of the page
   * @param name The name of the page
   */
  def parsePageFrontMatter(stream: InputStream, path: String, name: String, timezone: ZoneId): Page = {
    val yaml = extractFrontMatter(stream, timezone)

    val props = Yaml(yaml.map -- Seq("title", "permalink"))
    val title = yaml.getString("title")
    val fPermalink = yaml.getString("permalink")

    // Extract format from the name
    val (nPermalink, format) = name match {
      case NameFormatExtractor(p, f) => (p, f)
      case _ =>
        log.warn("Could not extract page format from name: {}", name)
        (name, "html")
    }

    val permalink = fPermalink.getOrElse(nPermalink + ".html")

    Page(permalink, format, path, title, props)
  }

  def extractFrontMatter(stream: InputStream, timezone: ZoneId): Yaml = {
    val lines = Source.fromInputStream(stream).getLines().map(_.trim())
      .dropWhile(_.isEmpty)

    // Extract attributes from the front matter
    lines.nextOption match {
      case Some("---") =>
        val frontMatter = lines.takeWhile(_ != "---").mkString("\n")
        Yaml.parse(frontMatter, timezone)
      case _ =>
        Yaml.empty
    }
  }
}



