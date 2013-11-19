package models

import org.joda.time.{YearMonth, DateTime}
import scala.collection.SortedMap
import scala.util.control.Exception._
import org.joda.time.format.DateTimeFormat

/**
 * The date a blog post was published
 */
final case class PostDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) {
  val toDateTime = new DateTime(year, month, day, hour, minute)
  lazy val format = PostDate.dateFormat.print(toDateTime)
  val orderKey = toDateTime.getMillis
}

object PostDate {
  implicit val ordering = Ordering.by((postDate: PostDate) => postDate.orderKey)

  val dateFormat = DateTimeFormat.forPattern("d MMMM yyyy")
}

/**
 * A day that has published blog posts
 */
final case class Day(year: Int, month: Int, day: Int, posts: List[BlogPost]) {

  /**
   * Get a post for the given permalink, if one exists
   */
  def forPermalink(permalinkTitle: String): Option[BlogPost] =
    posts.find(_.permalinkTitle.equalsIgnoreCase(permalinkTitle))

  /**
   * It's not sequential, but it does ensure that every day has a strictly increasing order key
   */
  val orderKey = year * 400 + month * 31 + day
}

object Day {
  implicit val ordering = Ordering.by((day: Day) => day.orderKey)
}

/**
 * A month that has published blog posts
 */
final case class Month(year: Int, month: Int, days: SortedMap[Int, Day], posts: List[BlogPost]) {

  /**
   * Get the posts for the given day
   */
  def forDay(day: Int): Day = days.get(day).getOrElse(Day(year, month, day, Nil))

  private lazy val partial = allCatch.opt(new YearMonth(year, month))

  lazy val name = partial.map(_.monthOfYear.getAsText).getOrElse("Unknown")

  val orderKey = year * 12 + month
}

object Month {
  implicit val ordering = Ordering.by((month: Month) => month.orderKey)
}

/**
 * A year that has published blog posts
 */
final case class Year(year: Int, months: SortedMap[Int, Month], posts: List[BlogPost]) {

  /**
   * Get the posts for the given month
   */
  def forMonth(month: Int): Month = months.get(month).getOrElse(Month(year, month, SortedMap.empty, Nil))
}

object Year {
  implicit val ordering = Ordering.by((year: Year) => year.year)
}