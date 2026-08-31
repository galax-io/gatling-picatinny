package org.galaxio.gatling.javaapi.assertions;

import io.gatling.javaapi.core.Assertion;
import org.galaxio.gatling.javaapi.OpenNfrAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compile guard for the facade's public surface (test-model layer 5).
 *
 * <p>This file failing to compile is the point: it pins the signature a Java or Kotlin simulation writes against, so a
 * change to it is a deliberate act rather than an accident.
 */
class JavaOpenNfrCompileTest {

    @Test
    @DisplayName("the entry point has the signature a simulation writes against")
    void signatureIsPinned() {
        // The call below is the guard: it must compile exactly as written.
        List<Assertion> assertions = OpenNfrAssertions.fromYaml("src/test/resources/opennfr/nfr.yaml");
        assertNotNull(assertions);
    }

    @Test
    @DisplayName("the facade is a utility class and cannot be instantiated")
    void isAUtilityClass() {
        assertTrue(Modifier.isFinal(OpenNfrAssertions.class.getModifiers()), "final");
        assertEquals(1, OpenNfrAssertions.class.getDeclaredConstructors().length, "one constructor");
        assertFalse(
                Modifier.isPublic(OpenNfrAssertions.class.getDeclaredConstructors()[0].getModifiers()),
                "and it is not public");
    }

    @Test
    @DisplayName("the deprecated twin is untouched and still present")
    void deprecatedTwinSurvives() throws NoSuchMethodException {
        // FR-015: the new surface sits beside the old one; it does not replace or wrap it.
        var deprecated = org.galaxio.gatling.javaapi.Assertions.class.getMethod("assertionFromYaml", String.class);
        assertTrue(deprecated.isAnnotationPresent(Deprecated.class), "still deprecated, still there");
    }
}
