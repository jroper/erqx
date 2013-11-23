package au.id.jazzy.erqx.engine.services

import java.io.InputStream
import scalax.io.Resource
import play.api.Logger
import org.joda.time.DateTime
import au.id.jazzy.erqx.engine.models._

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
    val (props, fTitle, fId, fDate, tags) = lines.nextOption match {
      case Some("---") =>
        val frontMatter = lines.takeWhile(_ != "---").mkString("\n")

        val props: Yaml = Yaml.parse(frontMatter)

        (
          Yaml(props.map -- Seq("title", "id", "date", "tags")),
          props.getString("title"),
          props.getString("id").map(_.toString),
          props.getDate("date"),
          props.getString("tags").toSeq.flatMap(_.split(" +").map(_.replace('+', ' ')))
        )

      case _ => (Yaml.empty, None, None, None, Nil)
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
          Some(new DateTime(year, month, day, 0, 0)),
          title
        )
      case _ => 
        Logger.warn("Blog post file name not extractable: " + name)
        (None, None, nId)
    }
    
    val id = fId getOrElse nId
    val title = fTitle orElse nTitle getOrElse id
    val date = fDate orElse nDate getOrElse new DateTime()
    
    BlogPost(id, path, title, date, permalinkTitle, format, tags.toSet, props)
  }

}



