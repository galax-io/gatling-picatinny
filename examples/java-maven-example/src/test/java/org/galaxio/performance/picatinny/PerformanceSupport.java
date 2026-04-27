package org.galaxio.performance.picatinny;

import io.gatling.javaapi.core.Assertion;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gatling.javaapi.core.CoreDsl.global;

public final class PerformanceSupport {
    private PerformanceSupport() {
    }

    public static scala.concurrent.duration.FiniteDuration toScala(Duration duration) {
        return scala.concurrent.duration.FiniteDuration.apply(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    public static scala.collection.immutable.Seq<io.gatling.commons.stats.assertion.Assertion> noFailedRequests() {
        Assertion assertion = global().failedRequests().count().is(0L);
        return scala.jdk.javaapi.CollectionConverters.asScala(List.of(assertion.asScala())).toSeq();
    }
}
