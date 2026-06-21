package org.galaxio.gatling.feeders

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for [[EnvFeeder]] (test-model layer 1). Uses the `fromEnv`/`withPrefixFrom` test seams with a FIXED environment
  * map so the cases are deterministic and assert exact keys AND values (instead of sampling the live, unspecified-order
  * `sys.env`). Covers present/missing/empty/null and real prefix-stripping (`PERF_TOKEN` → `TOKEN`).
  */
class EnvFeederSpec extends AnyWordSpec with Matchers {

  private val env    = Map("PERF_TOKEN" -> "secret", "PERF_USER" -> "alice", "OTHER" -> "x")
  private val absent = "__PICATINNY_DEFINITELY_NOT_SET__"

  "EnvFeeder.apply" should {

    "read present environment variables into a one-record feeder" in {
      EnvFeeder.fromEnv(List("PERF_TOKEN", "PERF_USER"), "", env) shouldBe
        IndexedSeq(Map("PERF_TOKEN" -> "secret", "PERF_USER" -> "alice"))
    }

    "skip missing variables and return empty when none are present (negative)" in {
      EnvFeeder.fromEnv(List(absent), "", env) shouldBe IndexedSeq.empty
    }

    "return empty for an empty keys list (boundary)" in {
      EnvFeeder.fromEnv(List.empty, "", env) shouldBe IndexedSeq.empty
    }

    "return empty against an empty environment (boundary)" in {
      EnvFeeder.fromEnv(List("PERF_TOKEN"), "", Map.empty) shouldBe IndexedSeq.empty
    }

    "keep only the present subset when some keys are missing" in {
      EnvFeeder.fromEnv(List("PERF_TOKEN", absent), "", env) shouldBe IndexedSeq(Map("PERF_TOKEN" -> "secret"))
    }

    "strip the prefix from the feeder key when the key starts with it" in {
      EnvFeeder.fromEnv(List("PERF_TOKEN"), "PERF_", env) shouldBe IndexedSeq(Map("TOKEN" -> "secret"))
    }

    "keep the original key when it does not start with the prefix" in {
      EnvFeeder.fromEnv(List("OTHER"), "PERF_", env) shouldBe IndexedSeq(Map("OTHER" -> "x"))
    }

    "reject a null keys list" in {
      a[NullPointerException] should be thrownBy EnvFeeder.fromEnv(null, "", env)
    }
  }

  "EnvFeeder.withPrefix" should {

    "collect variables matching the prefix with the prefix stripped" in {
      EnvFeeder.withPrefixFrom("PERF_", env) shouldBe IndexedSeq(Map("TOKEN" -> "secret", "USER" -> "alice"))
    }

    "return empty when no variable matches the prefix (negative)" in {
      EnvFeeder.withPrefixFrom(absent, env) shouldBe IndexedSeq.empty
    }

    "reject an empty prefix" in {
      an[IllegalArgumentException] should be thrownBy EnvFeeder.withPrefixFrom("", env)
    }
  }
}
