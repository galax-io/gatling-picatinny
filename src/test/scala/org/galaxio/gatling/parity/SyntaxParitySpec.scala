package org.galaxio.gatling.parity

import org.galaxio.gatling.templates.Syntax
import org.galaxio.gatling.templates.Syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Try

/** Live body assembly must equal the frozen reference, character for character (FR-011).
  *
  * This is the safety net the #126 rewrite is judged against. It must be GREEN at the commit that introduces it, with no
  * production file yet modified — that is what makes it a reference rather than a restatement of the new behaviour.
  *
  * If it goes red during an optimization, the live code changed observable output. The response is to narrow or drop the
  * optimization, never to edit an expectation or this suite's oracle.
  */
class SyntaxParitySpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  /** Compare the FULL observable outcome, not just the returned value: a rewrite that starts throwing where the original
    * returned would otherwise slip through.
    */
  private def outcome[A](a: => A): Either[String, A] =
    Try(a).toEither.left.map(t => s"${t.getClass.getName}: ${t.getMessage}")

  private def compareJson(fields: List[Field], clue: String): Unit =
    withClue(s"$clue\n") {
      outcome(Syntax.makeJson(fields)) shouldBe outcome(FrozenSyntaxReference.makeJson(fields))
    }

  private def compareXml(fields: List[Field], clue: String): Unit =
    withClue(s"$clue\n") {
      outcome(Syntax.makeXml(fields)) shouldBe outcome(FrozenSyntaxReference.makeXml(fields))
    }

  private def show(s: String): String = s.map(c => if (c < ' ') f"\\u${c.toInt}%04x" else c.toString).mkString

  "JSON body assembly" should {

    "match the frozen reference for every enumerated boundary string, as a VALUE" in {
      ParityGenerators.boundaryStrings.foreach { v =>
        compareJson(List("field" - v), s"value = [${show(v)}]")
      }
    }

    "match the frozen reference for every enumerated boundary string, as a NAME" in {
      // Field names go through the same escaper on every branch — the half a value-only rewrite misses.
      ParityGenerators.boundaryStrings.filter(_.nonEmpty).foreach { n =>
        compareJson(List(n - "v"), s"name = [${show(n)}]")
      }
    }

    "match the frozen reference over generated name/value pairs" in {
      forAll(ParityGenerators.fieldName, ParityGenerators.escapableString) { (n, v) =>
        compareJson(List(n - v), s"name = [${show(n)}], value = [${show(v)}]")
      }
    }

    "match the frozen reference for nested objects, arrays and expression references" in {
      forAll(ParityGenerators.escapableString, ParityGenerators.escapableString) { (a, b) =>
        val fields = List(
          "lit" - a,
          "nested" - ("inner" - b, "n" - 1),
          "arr" > (a, b, 1, true),
          "ref" ~ "sessionVar",
          "nul" - nullVal,
        )
        compareJson(fields, s"a = [${show(a)}], b = [${show(b)}]")
      }
    }

    "match on the empty field list" in {
      compareJson(Nil, "empty")
    }
  }

  "XML body assembly" should {

    "match the frozen reference for every enumerated boundary string, as a VALUE" in {
      ParityGenerators.boundaryStrings.foreach { v =>
        compareXml(List("field" - v), s"value = [${show(v)}]")
      }
    }

    "match the frozen reference for every enumerated boundary string, as a NAME" in {
      ParityGenerators.boundaryStrings.filter(_.nonEmpty).foreach { n =>
        compareXml(List(n - "v"), s"name = [${show(n)}]")
      }
    }

    "match the frozen reference over generated name/value pairs" in {
      forAll(ParityGenerators.fieldName, ParityGenerators.escapableString) { (n, v) =>
        compareXml(List(n - v), s"name = [${show(n)}], value = [${show(v)}]")
      }
    }

    "match the frozen reference for nested objects and arrays" in {
      forAll(ParityGenerators.escapableString) { a =>
        // Two child fields, not one: with a single argument `-` is ambiguous between the varargs
        // (nested object) and generic-value overloads.
        compareXml(List("outer" - ("inner" - a, "n" - 1), "arr" > (a, 2), "nul" - nullVal), s"a = [${show(a)}]")
      }
    }
  }

  "the escape sets themselves" should {

    "be exhaustively covered, not sampled — one character per branch, both directions" in {
      // Enumerating is the point: a predicate guarding a fast path must agree with the branch list
      // it short-circuits, and sampling can miss the single character where they diverge.
      ParityGenerators.JsonEscapeSet.foreach(c => compareJson(List("k" - s"a${c}b"), s"json escape [${show(c.toString)}]"))
      ParityGenerators.XmlEscapeSet.foreach(c => compareXml(List("k" - s"a${c}b"), s"xml escape [${show(c.toString)}]"))
      ParityGenerators.PassThrough.foreach { c =>
        compareJson(List("k" - s"a${c}b"), s"json passthrough [${show(c.toString)}]")
        compareXml(List("k" - s"a${c}b"), s"xml passthrough [${show(c.toString)}]")
      }
    }

    "cover every C0 control character, since only some have a dedicated short escape" in {
      (0 until 32).foreach { i =>
        compareJson(List("k" - s"a${i.toChar}b"), f"control \\u$i%04x")
        compareXml(List("k" - s"a${i.toChar}b"), f"control \\u$i%04x")
      }
    }
  }
}
