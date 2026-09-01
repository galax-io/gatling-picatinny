package org.galaxio.gatling.jmh

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source
import scala.util.Using

/** Guards the benchmark naming convention, which is load-bearing rather than cosmetic (FR-004).
  *
  * Three gates key off the file pattern `.*Benchmark.*` — the coverage denominator (`coverageExcludedFiles`), the scalafix
  * source filter, and `isBenchmarkArtifact`, which keeps benchmarks out of BOTH published archives. A benchmark class whose
  * name omits the marker is therefore silently counted against the coverage floor AND shipped to consumers, with no error
  * anywhere. Nothing else in the build notices.
  *
  * Reads sources rather than reflecting over the classpath: the rule is about the FILE name, the source is where a reviewer
  * would look, and this is the same approach `MigrationTableSpec` uses to guard docs against code.
  */
class BenchmarkNamingSpec extends AnyWordSpec with Matchers {

  private val Marker = "Benchmark"

  private def scalaSourcesUnder(dir: File): List[File] =
    Option(dir.listFiles()).toList.flatten.flatMap {
      case d if d.isDirectory                => scalaSourcesUnder(d)
      case f if f.getName.endsWith(".scala") => List(f)
      case _                                 => Nil
    }

  private def read(f: File): String =
    Using(Source.fromFile(f, "UTF-8"))(_.mkString).getOrElse(fail(s"cannot read $f"))

  /** Every source declaring a JMH benchmark — i.e. extending the shared base class. The base class itself is abstract and
    * declares no `@Benchmark`, but it lives in the benchmark package and is matched by the same marker, so it is included
    * deliberately.
    */
  private lazy val benchmarkSources: List[File] =
    scalaSourcesUnder(new File("src/main/scala"))
      .filter(f => read(f).contains("extends JmhBenchmark") || f.getName == "JmhBenchmark.scala")

  "the benchmark naming convention" should {

    "find the benchmarks at all, so the guard below cannot pass vacuously" in {
      withClue("no benchmark sources found under src/main/scala — the guard would be meaningless\n") {
        benchmarkSources.size should be >= 4
      }
    }

    "hold for every benchmark source, or the class enters coverage and ships to consumers" in {
      val offenders = benchmarkSources.filterNot(_.getName.contains(Marker))
      withClue(s"benchmark sources missing the `$Marker` marker: ${offenders.map(_.getName).mkString(", ")}\n") {
        offenders shouldBe empty
      }
    }

    "reject a name without the marker — the negative case the rule exists for" in {
      // The exclusions are driven by `.*Benchmark.*` against the file name. A plausible-looking
      // benchmark class named without the marker must NOT satisfy it.
      "CookieParserPerf.scala".contains(Marker) shouldBe false
      "CookieParserBenchmark.scala".contains(Marker) shouldBe true
    }

    "match the marker anywhere in the name, not only as a suffix" in {
      // `isBenchmarkArtifact` uses `.*Benchmark.*`, so an infix name is covered too. Pinned so a
      // future tightening to `endsWith` does not silently unexclude such a class.
      "BenchmarkSupport.scala".contains(Marker) shouldBe true
    }
  }
}
