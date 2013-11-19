package services

import java.io.InputStream
import models.{PostDate, BlogPost}
import scalax.io.Resource
import play.api.Logger

object MetaDataParser {

  val NameFormatExtractor = """(.+)\.([^\.]+)""".r
  val NameAttributeExtractor = """(\d{4})-(\d{1,2})-(\d{1,2})-(.+)""".r
  val DateTimeParser = """(\d{4})/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{2})""".r
  val DateParser = """(\d{4})/(\d{1,2})/(\d{1,2})""".r
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
  def parseFrontMatter(stream: InputStream, path: String, name: String): BlogPost = {
    val lines = Resource.fromInputStream(stream).lines().toIterator.map(_.trim())
      .dropWhile(_.isEmpty)

    // Extract attributes from the front matter
    val (fTitle, fId, fDate, tags) = lines.nextOption match {
      case Some("---") =>
        val frontMatter = lines.takeWhile(_ != "---")
        val props = frontMatter.filterNot(_.isEmpty)
          .map { line =>
            val keyValue = line.split(":", 2)
            val key = keyValue.head.trim
            val value = keyValue.tail.headOption.getOrElse("").trim
            key -> value
          }.toMap
        (
          props.get("title"),
          props.get("id"),
          props.get("date").flatMap {
            case DateTimeParser(ToInt(year), ToInt(month), ToInt(day), ToInt(hour), ToInt(minute)) =>
              Some(PostDate(year, month, day, hour, minute))
            case DateParser(ToInt(year), ToInt(month), ToInt(day)) =>
              Some(PostDate(year, month, day))
            case error =>
              Logger.warn("Unparseable date: " + error)
              None
          },
          props.get("tags").toSeq.flatMap(_.split(" +")).map(_.replace('+', ' '))
        )

      case _ => (None, None, None, Seq())
    }

    // Extract id and format from the name
    val (nId, format) = name match {
      case NameFormatExtractor(id, format) => (id, format)
      case _ =>
        Logger.warn("Could not extract blog post format from name: " + name)
        (name, "html")
    }

    // Extract attributes from the name
    val (nTitle, nDate, permalinkTitle) = nId match {
      case NameAttributeExtractor(ToInt(year), ToInt(month), ToInt(day), title) =>
        (
          Some(title.replace('_', ' ')),
          Some(PostDate(year, month, day)),
          title
        )
      case _ => 
        Logger.warn("Blog post file name not extractable: " + name)
        (None, None, nId)
    }
    
    val id = fId getOrElse nId
    val title = fTitle orElse nTitle getOrElse id
    val date = fDate orElse nDate getOrElse PostDate(0, 0, 0)
    
    BlogPost(id, path, title, date, permalinkTitle, format, tags.toSet)
  }

}
