package services

import org.specs2.mutable.Specification
import java.io.ByteArrayInputStream
import models.{PostDate, BlogPost}

class MetaDataParserSpec extends Specification {

  "meta data parser" should {
    "extract data from the front matter" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "id" -> "someid",
        "title" -> "Some title",
        "date" -> "2013/11/16",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("someid", "some/path", "Some title", PostDate(2013, 11, 16), "some_name", "md", Set("foo", "bar"))
    }
    "use name as id if not specified" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "title" -> "Some title",
        "date" -> "2013/11/16",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "Some title", PostDate(2013, 11, 16), "some_name", "md",
          Set("foo", "bar"))
    }
    "extract title from name if not specified" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "id" -> "someid",
        "date" -> "2013/11/16",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("someid", "some/path", "some name", PostDate(2013, 11, 16), "some_name", "md", Set("foo", "bar"))
    }
    "extract date from name if not specified" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "id" -> "someid",
        "title" -> "Some title",
        "tags" -> "foo bar"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("someid", "some/path", "Some title", PostDate(2012, 10, 15), "some_name", "md", Set("foo", "bar"))
    }
    "work from name if nothing specified" in {
      MetaDataParser.parseFrontMatter(frontMatter(
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", PostDate(2012, 10, 15), "some_name", "md", Set())
    }
    "allow dates with hours and minutes" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "date" -> "2013/11/16 13:33"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", PostDate(2013, 11, 16, 13, 33), "some_name", "md", Set())
    }
    "allow tags with escaped spaces" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "tags" -> "foo+bar"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", PostDate(2012, 10, 15), "some_name", "md", Set("foo bar"))
    }
    "work from name if no front matter" in {
      MetaDataParser.parseFrontMatter(new ByteArrayInputStream("Hello".getBytes("UTF-8")),
        "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "some name", PostDate(2012, 10, 15), "some_name", "md", Set())
    }
    "allow colons in the title" in {
      MetaDataParser.parseFrontMatter(frontMatter(
        "title" -> "This: is a blog post"
      ), "some/path", "2012-10-15-some_name.md") ===
        BlogPost("2012-10-15-some_name", "some/path", "This: is a blog post", PostDate(2012, 10, 15), "some_name", "md", Set())
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

}
