package org.galaxio.gatling.feeders

import io.gatling.core.config.GatlingConfiguration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TransformFeedersSpec extends AnyWordSpec with Matchers {

  private implicit val configuration: GatlingConfiguration = GatlingConfiguration.loadForTest()

  "SeparatedValuesFeeder" should {
    "split every value from a sequence of maps" in {
      val source = Vector(
        Map(
          "HOSTS" -> "host11;host12",
          "USERS" -> "user11",
        ),
        Map(
          "HOSTS" -> "host21;host22",
          "USERS" -> "user21;user22;user23",
        ),
      )

      SeparatedValuesFeeder(None, source, ';') shouldBe Vector(
        Map("HOSTS" -> "host11"),
        Map("HOSTS" -> "host12"),
        Map("USERS" -> "user11"),
        Map("HOSTS" -> "host21"),
        Map("HOSTS" -> "host22"),
        Map("USERS" -> "user21"),
        Map("USERS" -> "user22"),
        Map("USERS" -> "user23"),
      )
    }

    "split every value from a sequence of strings" in {
      SeparatedValuesFeeder("rndString", Seq("1\ttwo", "3\t4"), '\t') shouldBe Vector(
        Map("rndString" -> "1"),
        Map("rndString" -> "two"),
        Map("rndString" -> "3"),
        Map("rndString" -> "4"),
      )
    }

    "split every value from a single string" in {
      SeparatedValuesFeeder("rndString", "host11,host12", ',') shouldBe Array(
        Map("rndString" -> "host11"),
        Map("rndString" -> "host12"),
      )
    }

    "include param name in error message for empty Seq source" in {
      val ex = the[IllegalArgumentException] thrownBy {
        SeparatedValuesFeeder("myParam", Seq.empty[String], ',')
      }
      ex.getMessage should include("myParam")
      ex.getMessage should include("source sequence is empty")
    }

    "include prefix in error message for empty map sequence source" in {
      val ex = the[IllegalArgumentException] thrownBy {
        SeparatedValuesFeeder(Some("pfx"), Seq.empty[Map[String, Any]], ',')
      }
      ex.getMessage should include("pfx")
      ex.getMessage should include("source map sequence is empty")
    }

    "handle null values in map source without throwing NPE" in {
      val source = Seq(Map("KEY" -> (null: Any), "OTHER" -> "a,b"))

      val result = SeparatedValuesFeeder(None, source, ',')

      result should contain(Map("KEY" -> "null"))
      result should contain(Map("OTHER" -> "a"))
      result should contain(Map("OTHER" -> "b"))
    }
  }
}
