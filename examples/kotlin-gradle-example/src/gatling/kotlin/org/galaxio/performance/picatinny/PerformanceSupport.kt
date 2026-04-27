package org.galaxio.performance.picatinny

import io.gatling.commons.stats.assertion.Assertion
import io.gatling.javaapi.core.CoreDsl.global
import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.jdk.javaapi.CollectionConverters
import java.time.Duration
import java.util.concurrent.TimeUnit

object PerformanceSupport {
    fun toScala(duration: Duration): FiniteDuration =
        FiniteDuration.apply(duration.toNanos(), TimeUnit.NANOSECONDS)

    fun noFailedRequests(): Seq<Assertion> {
        val assertion = global().failedRequests().count().shouldBe(0L)
        return CollectionConverters.asScala(listOf(assertion.asScala())).toSeq()
    }
}
