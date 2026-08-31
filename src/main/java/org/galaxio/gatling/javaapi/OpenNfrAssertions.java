package org.galaxio.gatling.javaapi;

import io.gatling.javaapi.core.Assertion;

import java.util.List;

/**
 * Builds Gatling assertions from an <a href="https://github.com/galax-io/opennfr">OpenNFR</a>
 * {@code RequirementSet}, for Java and Kotlin simulations.
 *
 * <p><b>Experimental.</b> OpenNFR is pre-1.0 and moves; this tracks release {@code v0.6.0} of it, and the surface is outside
 * the binary-compatibility guarantee the rest of this library keeps. The deprecated {@code Assertions.assertionFromYaml}
 * is untouched and keeps its guarantees in full.
 *
 * <p>Unlike that deprecated twin — which had to duplicate the Scala builder's key mapping — this facade duplicates nothing.
 * Every rule of the format is decided in the Scala core, which also performs the Java-DSL application, so this class is a
 * pass-through and there is no second place a rule can drift.
 */
public final class OpenNfrAssertions {

    private OpenNfrAssertions() {
    }

    /**
     * Builds the assertions an OpenNFR document denotes.
     *
     * @param path path to the document
     * @return one assertion per criterion and per guard
     * @throws org.galaxio.gatling.assertions.opennfr.OpenNfrException if the document cannot be read, is not an OpenNFR
     *     document, or carries a predicate Gatling cannot assert — carrying every reason, not the first
     */
    public static List<Assertion> fromYaml(String path) {
        return org.galaxio.gatling.assertions.opennfr.JavaRender.assertions(path);
    }
}
