package org.galaxio.gatling.javaapi;

import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;

/**
 * Convenience diagnostics facade for Java and Kotlin Gatling simulations.
 *
 * <p>Prefer {@code banner(OpenInjectionStep...)} or {@code banner(ClosedInjectionStep...)} when a simulation has
 * explicit Gatling injection steps. Use {@code banner()} only when the banner must fall back to {@code simulation.conf}
 * workload parameters.</p>
 */
public final class Utility {

    private Utility() {
    }

    /**
     * Prints the startup banner from {@code simulation.conf} workload settings when enabled.
     */
    public static void banner() {
        org.galaxio.gatling.utils.Utility.banner();
    }

    /**
     * Prints the startup banner by parsing Java/Kotlin Gatling open injection steps.
     *
     * @param steps open injection steps passed to {@code injectOpen}
     */
    public static void banner(OpenInjectionStep... steps) {
        org.galaxio.gatling.utils.Utility.banner(steps);
    }

    /**
     * Prints the startup banner by parsing Java/Kotlin Gatling closed injection steps.
     *
     * @param steps closed injection steps passed to {@code injectClosed}
     */
    public static void banner(ClosedInjectionStep... steps) {
        org.galaxio.gatling.utils.Utility.banner(steps);
    }

    /**
     * Prints runtime/JVM diagnostics when enabled.
     */
    public static void diagnostics() {
        org.galaxio.gatling.utils.Utility.diagnostics();
    }
}
