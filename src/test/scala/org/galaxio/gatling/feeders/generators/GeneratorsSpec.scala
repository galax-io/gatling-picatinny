package org.galaxio.gatling.feeders.generators

import org.galaxio.gatling.feeders.generators.Implicits._
import org.galaxio.gatling.feeders.generators.{oneOf => genOneOf}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

/** Unit suite for the `feeders/generators` package. Deterministic via a fixed-seed [[GeneratorContext]]. */
class GeneratorsSpec extends AnyWordSpec with Matchers {

  private def ctx(seed: Long): GeneratorContext =
    GeneratorContext(new Random(seed), SizeBounds.default, 30)

  private def draw[T](g: Generator[T], seed: Long): T = g(ctx(seed)).value

  "long()" should {
    "respect bounds well outside the Int range" in {
      val (min, max) = (-3_000_000_000L, 3_000_000_000L)
      val c          = ctx(1L)
      (1 to 100000).foreach { _ =>
        val v = long(min, max)(c).value
        v should be >= min
        v should be <= max
      }
    }

    "never return a negative value for long(0, Long.MaxValue)" in {
      val c = ctx(2L)
      (1 to 100000).foreach { _ =>
        long(0L, Long.MaxValue)(c).value should be >= 0L
      }
    }

    "span the full range for long(Long.MinValue, Long.MaxValue)" in {
      val c      = ctx(3L)
      val values = (1 to 1000).map(_ => long(Long.MinValue, Long.MaxValue)(c).value)
      values.exists(_ < 0L) shouldBe true
      values.exists(_ > 0L) shouldBe true
    }

    "be deterministic for a fixed seed" in {
      draw(long(0L, 1_000_000_000_000L), 7L) shouldBe draw(long(0L, 1_000_000_000_000L), 7L)
    }
  }

  "numeric generators" should {
    "produce int within the inclusive range" in {
      val c = ctx(10L)
      (1 to 1000).foreach(_ => int(1, 6)(c).value should (be >= 1 and be <= 6))
    }

    "produce int at the exact boundary when min == max" in {
      draw(int(5, 5), 1L) shouldBe 5
    }

    "produce positiveLong as non-negative" in {
      val c = ctx(11L)
      (1 to 1000).foreach(_ => positiveLong(c).value should be >= 0L)
    }
  }

  "string generators" should {
    "produce alphaStringN of the requested length, letters only" in {
      val s = draw(alphaStringN(12), 20L)
      s should have length 12
      s should fullyMatch regex "[A-Za-z]{12}"
    }

    "produce numberStringN of digits only" in {
      draw(numberStringN(8), 21L) should fullyMatch regex "\\d{8}"
    }

    "produce an empty string for length 0 (boundary)" in {
      draw(alphaStringN(0), 22L) shouldBe ""
    }
  }

  "character generators" should {
    "produce digitChar only in 0-9" in {
      val c = ctx(30L)
      (1 to 1000).foreach(_ => digitChar(c).value should (be >= '0' and be <= '9'))
    }

    "produce upperChar only in A-Z" in {
      val c = ctx(31L)
      (1 to 1000).foreach(_ => upperChar(c).value should (be >= 'A' and be <= 'Z'))
    }
  }

  "collection generators" should {
    "oneOf(seq) returns only members" in {
      val members = Seq("a", "b", "c")
      val c       = ctx(40L)
      (1 to 1000).foreach(_ => members should contain(genOneOf(members)(c).value))
    }

    "oneOf(varargs) returns only members" in {
      val c = ctx(41L)
      (1 to 1000).foreach(_ => Seq(1, 2, 3) should contain(genOneOf(1, 2, 3)(c).value))
    }

    "listOfN produces a list of the requested size" in {
      draw(listOfN(int(0, 9), 5), 42L) should have length 5
    }
  }

  "combinators" should {
    "~ concatenates two string generators" in {
      draw(const("a") ~ const("b"), 1L) shouldBe "ab"
    }

    "** repeats a generator n times" in {
      draw(const("x") ** 3, 1L) shouldBe "xxx"
    }

    "repeat().separateBy() joins with a separator" in {
      draw(const("x").repeat(3).separateBy("-"), 1L) shouldBe "x-x-x"
    }

    "** with n == 1 returns the single value" in {
      draw(const("y") ** 1, 1L) shouldBe "y"
    }

    "** rejects n == 0 with IllegalArgumentException" in {
      val ex = the[IllegalArgumentException] thrownBy (const("x") ** 0)
      ex.getMessage should include("0")
    }

    "** rejects negative n with IllegalArgumentException" in {
      an[IllegalArgumentException] should be thrownBy (const("x") ** -1)
    }

    "repeat rejects n == 0 with IllegalArgumentException" in {
      an[IllegalArgumentException] should be thrownBy const("x").repeat(0)
    }

    "separateBy rejects n == 0 with IllegalArgumentException" in {
      an[IllegalArgumentException] should be thrownBy Syntax.SeparatorStep(const("x"), 0).separateBy(",")
    }
  }

  "derivation" should {
    "derive a Generator for a case class from its fields" in {
      val p = draw(implicitly[Generator[GeneratorsSpec.Point]], 50L)
      p shouldBe a[GeneratorsSpec.Point]
    }
  }

  "determinism" should {
    "produce identical output for the same seed" in {
      draw(alphanumericStringN(16), 99L) shouldBe draw(alphanumericStringN(16), 99L)
    }
  }
}

object GeneratorsSpec {
  final case class Point(x: Int, y: Long, label: String)
}
