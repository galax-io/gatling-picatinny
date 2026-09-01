package org.galaxio.gatling.javaapi.assertions;

import io.gatling.javaapi.core.Assertion;
import org.galaxio.gatling.javaapi.OpenNfrAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Facade delegation (test-model layer 6, FR-016).
 *
 * <p>The point of these tests is not that the facade works — it is that the facade <i>decides nothing</i>. Every rule lives
 * in the Scala core, which also performs the Java-DSL application, so this class is a pass-through. What is asserted here is
 * that the same document reaches the same verdict, and that a refusal carries the core's reasons verbatim.
 *
 * <p>That the two surfaces build the <i>same underlying assertion values</i> is asserted from Scala, in
 * {@code FacadeParitySpec}, where both surfaces are reachable without Scala default arguments.
 */
class JavaOpenNfrAssertionsTest {

    private static final String TRANSLATED = "src/test/resources/opennfr/nfr.yaml";

    @Test
    @DisplayName("builds the eleven assertions the translated fixture denotes")
    void buildsTheSameEleven() {
        // Ten until upstream v0.8.0 minted `loadtest.group.duration` (#328), which made the
        // group-only requirement renderable and parity with the deprecated builder WHOLE.
        List<Assertion> assertions = OpenNfrAssertions.fromYaml(TRANSLATED);
        assertEquals(11, assertions.size(), "the translation denotes eleven assertions; see contracts/parity.md");
        assertEquals(11, assertions.stream().distinct().count(), "no duplicates");
    }

    @Test
    @DisplayName("refuses an unrenderable document for the same reasons, proving no decision was re-made")
    void refusesForTheSameReasons() {
        // `group-only.yaml` RENDERS since v0.8.0, so the refusal fixture is now the retired metric
        // name — dropped outright upstream, never aliased.
        var e = assertThrows(
                org.galaxio.gatling.assertions.opennfr.OpenNfrException.class,
                () -> OpenNfrAssertions.fromYaml("src/test/resources/opennfr/retired-metric.yaml"));

        assertEquals(1, e.reasons().size(), "one predicate, one reason");
        assertTrue(
                e.getMessage().contains("retired in OpenNFR v0.8.0"),
                "the reason is the one upstream decided, carried verbatim from the core: " + e.getMessage());
        assertTrue(
                e.getMessage().contains("loadtest.request.duration"),
                "the refusal must name the replacement to write instead: " + e.getMessage());
    }

    @Test
    @DisplayName("names the path when the document cannot be read")
    void namesTheMissingPath() {
        var e = assertThrows(
                org.galaxio.gatling.assertions.opennfr.OpenNfrException.class,
                () -> OpenNfrAssertions.fromYaml("src/test/resources/opennfr/does-not-exist.yaml"));
        assertTrue(e.getMessage().contains("does-not-exist.yaml"));
    }
}
