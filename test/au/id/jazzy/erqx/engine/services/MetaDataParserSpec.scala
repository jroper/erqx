package au.id.jazzy.erqx.engine.services

import org.specs2.mutable.Specification
import java.io.ByteArrayInputStream
import java.time.{ZoneOffset, ZonedDateTime}

import au.id.jazzy.erqx.engine.models.BlogPost

class MetaDataParserSpec extends Specification {

  "meta data parser" should {
    "extract data from the front matter" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "id" -> "someid",
        "title" -> "Some title",
        "date" -> "2013-11-16T00:00:00.0Z",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("someid", "some/path", "Some title", date(2013, 11, 16), "some_name", "md", Set("foo", "bar"))
    }
    "use name as id if not specified" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "title" -> "Some title",
        "date" -> "2013-11-16T00:00:00.0Z",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "Some title", date(2013, 11, 16), "some_name", "md",
          Set("foo", "bar"))
    }
    "extract title from name if not specified" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "id" -> "someid",
        "date" -> "2013-11-16T00:00:00.0Z",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("someid", "some/path", "some name", date(2013, 11, 16), "some_name", "md", Set("foo", "bar"))
    }
    "extract date from name if not specified" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "id" -> "someid",
        "title" -> "Some title",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("someid", "some/path", "Some title", date(2012, 10, 15), "some_name", "md", Set("foo", "bar"))
    }
    "work from name if nothing specified" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", date(2012, 10, 15), "some_name", "md", Set())
    }
    "allow dates with hours and minutes" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "date" -> "2013-11-16T13:33:00.0Z"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", date(2013, 11, 16, 13, 33), "some_name", "md", Set())
    }
    "allow tags with escaped spaces" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "tags" -> "foo+bar"
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", date(2012, 10, 15), "some_name", "md", Set("foo bar"))
    }
    "work from name if no front matter" in {
      MetaDataParser.parsePostFrontMatter(new ByteArrayInputStream("Hello".getBytes("UTF-8")),
        "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", date(2012, 10, 15), "some_name", "md", Set())
    }
    "allow colons in the title" in {
      MetaDataParser.parsePostFrontMatter(frontMatter(
        "title" -> "\"This: is a blog post\""
      ), "some/path", "2012-10-15-some_name.md", ZoneOffset.UTC) ===
        BlogPost("2012-10-15-some_name", "some/path", "This: is a blog post", date(2012, 10, 15), "some_name", "md", Set())
    }
  }

  def frontMatter(props: (String, String)*) = {
    new ByteArrayInputStream(
      """
        |---
        |%s
        |---
        |foo content
        |title: foo
      """.stripMargin.format(
        props.map(p => p._1 + ": " + p._2).mkString("\n")
      ).getBytes("UTF-8"))
  }

  def date(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) = {
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)
  }

}
