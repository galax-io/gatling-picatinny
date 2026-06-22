package org.galaxio.gatling.javaapi.assertions;

import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.galaxio.gatling.javaapi.Assertions.assertionFromYaml;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import io.gatling.javaapi.core.Assertion;

import org.galaxio.gatling.javaapi.AssertionBuilderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests for the deprecated Java {@code assertionFromYaml} facade (test-model layers: Facade Delegation + Unit). The facade is
 * deprecated as of v1.18.0 but still tested for correctness; {@code @SuppressWarnings("deprecation")} silences the expected
 * deprecation notice at the call sites.
 */
@SuppressWarnings("deprecation")
class JavaAssertionFromYamlTest {

    /** Normalize Java assertions to the underlying core (Scala case class) assertions, which have structural equality. */
    private static Set<io.gatling.commons.stats.assertion.Assertion> core(List<Assertion> assertions) {
        return assertions.stream().map(Assertion::asScala).collect(Collectors.toSet());
    }

    private static Set<io.gatling.commons.stats.assertion.Assertion> core(Assertion... assertions) {
        return Stream.of(assertions).map(Assertion::asScala).collect(Collectors.toSet());
    }

    /** The exact 11 assertions nfr.yml must produce (APDEX + RPS are unrecognised → skipped). error-rate thresholds are
     * Double, response-time percentile/max thresholds are Int. */
    private static Set<io.gatling.commons.stats.assertion.Assertion> expectedNfrYaml() {
        return core(
                global().responseTime().percentile(99.0).lt(1500),
                details("myGroup", "GET /test/id").responseTime().percentile(99.0).lt(1500),
                details("GET /test/email").responseTime().percentile(99.0).lt(400),
                global().responseTime().percentile(95.0).lt(1200),
                details("myGroup", "GET /test/id").responseTime().percentile(95.0).lt(1200),
                details("myGroup").responseTime().percentile(95.0).lt(1600),
                details("GET /test/email").responseTime().percentile(95.0).lt(320),
                global().failedRequests().percent().lt(5.0),
                details("GET /test/uuid").failedRequests().percent().lt(1.0),
                global().responseTime().max().lt(2000),
                details("GET /test/uuid").responseTime().max().lt(1000));
    }

    @Test
    void buildsOneAssertionPerEntryWithNoDuplication() { // FR-001
        assertThat(assertionFromYaml("src/test/resources/nfr.yml")).hasSize(11);
        assertThat(assertionFromYaml("src/test/resources/nfrSingle.yml")).hasSize(1);
    }

    @Test
    void matchesExpectedScopesAndThresholds() { // FR-002 parity baseline, FR-006 cyrillic keys, FR-001 error-rate Double
        assertThat(core(assertionFromYaml("src/test/resources/nfr.yml"))).isEqualTo(expectedNfrYaml());
        // fractional error-rate parses as a Double assertion (crashed on the Scala toInt path before the fix)
        assertThat(core(assertionFromYaml("src/test/resources/nfrSingle.yml")))
                .isEqualTo(core(global().failedRequests().percent().lt(5.5)));
    }

    @Test
    void unknownKeyLogsWarnAndIsSkipped() { // FR-003
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.galaxio.gatling.javaapi.Assertions");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level prev = logger.getLevel();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            assertThat(assertionFromYaml("src/test/resources/nfr.yml")).hasSize(11); // APDEX + RPS skipped
            List<String> warns = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(warns).anyMatch(m -> m.contains("APDEX"));
            assertThat(warns).anyMatch(m -> m.contains("RPS"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(prev);
        }
    }

    @Test
    void nonNumericValueFailsNamingMetricKeyAndValue() { // FR-004
        assertThatThrownBy(() -> assertionFromYaml("src/test/resources/nfrNonNumeric.yml"))
                .isInstanceOf(AssertionBuilderException.class)
                .hasMessageContaining("99 перцентиль времени выполнения") // the metric record key, not the scope key
                .hasMessageContaining("abc");
    }

    @Test
    void exceptionExposesMessageAndCause() { // FR-005
        try {
            assertionFromYaml("perf/nfr1.yml"); // missing file → AssertionBuilderException carrying a cause
            org.junit.jupiter.api.Assertions.fail("expected AssertionBuilderException");
        } catch (AssertionBuilderException ex) {
            assertThat(ex.getMessage()).isEqualTo("NFR File not found perf/nfr1.yml");
            assertThat(ex.getCause()).isInstanceOf(FileNotFoundException.class);
            assertThat(ex.getMessage()).isNotNull();
            assertThat(ex.getCause()).isNotNull();
        }
    }

    @Test
    void repeatedLoadIsIdentical() { // FR-007
        assertThat(core(assertionFromYaml("src/test/resources/nfr.yml")))
                .isEqualTo(core(assertionFromYaml("src/test/resources/nfr.yml")));
    }

    @Test
    void publicSurfaceIsDeprecated() throws Exception { // FR-012, F7
        assertThat(org.galaxio.gatling.javaapi.Assertions.class
                .getMethod("assertionFromYaml", String.class)
                .isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(AssertionBuilderException.class.isAnnotationPresent(Deprecated.class)).isTrue();
    }

    @ParameterizedTest(name = "path ''{0}'' fails with ''{1}''")
    @CsvSource({
        "src/test/resources/nfrInvalid.yml, Incorrect file content src/test/resources/nfrInvalid.yml",
        "'', NFR File not found",
        "perf/nfr1.yml, NFR File not found perf/nfr1.yml"
    })
    void shouldReportYamlAssertionErrors(String path, String message) {
        assertThatThrownBy(() -> assertionFromYaml(path))
                .isInstanceOf(AssertionBuilderException.class)
                .extracting(error -> ((AssertionBuilderException) error).msg().trim())
                .isEqualTo(message);
    }
}
