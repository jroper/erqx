//#fullcode
import org.specs2.mutable.Specification

class Foo extends Specification {

  "my code snippet" should {
    "work" in {

      //#mycode
      val sum = List(3, 5, 8)
        .reduce(_ + _)
      //#mycode

      sum must_== 16
    }
  }

}
//#fullcode
