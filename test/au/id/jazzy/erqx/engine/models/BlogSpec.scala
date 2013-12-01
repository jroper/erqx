package au.id.jazzy.erqx.engine.models

import org.specs2.mutable.Specification
import org.specs2.matcher.Matcher
import org.joda.time.DateTime

class BlogSpec extends Specification {

  "a blog" should {
    val sampleBlog = blog(
      post("b", 2013, 11, 16, "foo", "bar"), post("c", 2013, 11, 15, "bar"), post("a", 2013, 11, 18, "foo"),
      post("d", 2013, 10, 19), post("f", 2012, 1, 3), post("e", 2012, 12, 1)
    )
    
    "provide all posts sorted" in {
      sampleBlog.posts must contain(exactly(id("a"), id("b"), id("c"), id("d"), id("e"), id("f")).inOrder)
    }

    "allow post lookup by year" in {
      sampleBlog.forYear(2013).posts must contain(exactly(id("a"), id("b"), id("c"), id("d")).inOrder)
    }

    "allow post lookup by year and month" in {
      sampleBlog.forYear(2013).forMonth(11).posts must contain(exactly(id("a"), id("b"), id("c")).inOrder)
    }

    "allow post lookup by year, month and day" in {
      sampleBlog.forYear(2013).forMonth(11).forDay(15).posts must contain(exactly(id("c")).inOrder)
    }

    "gracefully handle non existent year/month/day lookups" in {
      sampleBlog.forYear(2000).forMonth(1).forDay(1).posts must beEmpty
    }

    "gracefully handle invalid year/month/day/lookups" in {
      sampleBlog.forYear(2000).forMonth(13).forDay(32).posts must beEmpty
    }

    "allow post lookup by permalink" in {
      sampleBlog.forYear(2013).forMonth(11).forDay(15).forPermalink("c") must beSome.like {
        case post => post.id === "c"
      }
    }

    "gracefully handle non existent lookup by permalink" in {
      sampleBlog.forYear(2013).forMonth(11).forDay(15).forPermalink("a") must beNone
    }

    "allow post lookup by tag" in {
      sampleBlog.forTag("foo") must beSome.like {
        case posts => posts must contain(exactly(id("a"), id("b")).inOrder)
      }
    }

  }

  def id(s: String): Matcher[BlogPost] = { post: BlogPost =>
    post.id === s
  }

  def post(id: String, year: Int, month: Int, day: Int, tags: String*) =
    BlogPost(id, id, id, new DateTime(year, month, day, 0, 0), id, "md", Set(tags:_*), Yaml.empty)
  def blog(posts: BlogPost*) = new Blog("", posts.toList, Nil, "", "", BlogInfo("", None, "", ""))

}
