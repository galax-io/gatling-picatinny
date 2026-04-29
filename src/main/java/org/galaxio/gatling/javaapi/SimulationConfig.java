package org.galaxio.gatling.javaapi;

import com.typesafe.config.Config;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static scala.jdk.javaapi.DurationConverters.toJava;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

/**
 * Java and Kotlin facade for reading Picatinny simulation settings from {@code simulation.conf}.
 *
 * <p>JVM system properties override values from {@code simulation.conf}, so CI and environment-specific runs can pass
 * values such as {@code -DbaseUrl=https://test.example.org} or {@code -Dintensity="120 rpm"}.</p>
 *
 * <p>Required getters throw {@link org.galaxio.gatling.config.SimulationConfigException} when a value is missing or has
 * an invalid type. Optional getters return {@code Optional.empty()} when the path is not defined.</p>
 */
public final class SimulationConfig {

    /**
     * Reads a required string parameter.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not a string
     */
    public static String getStringParam(String path) {
        return org.galaxio.gatling.config.SimulationConfig.getStringParam(requirePath(path));
    }

    /**
     * Reads a required integer parameter.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not an integer
     */
    public static Integer getIntParam(String path) {
        return org.galaxio.gatling.config.SimulationConfig.getIntParam(requirePath(path));
    }

    /**
     * Reads a required double parameter.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not numeric
     */
    public static Double getDoubleParam(String path) {
        return org.galaxio.gatling.config.SimulationConfig.getDoubleParam(requirePath(path));
    }

    /**
     * Reads a required duration parameter.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not a duration
     */
    public static Duration getDurationParam(String path) {
        return toJava(org.galaxio.gatling.config.SimulationConfig.getDurationParam(requirePath(path)));
    }

    /**
     * Reads a required boolean parameter.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not a boolean
     */
    public static Boolean getBooleanParam(String path) {
        return org.galaxio.gatling.config.SimulationConfig.getBooleanParam(requirePath(path));
    }

    /**
     * Reads a required list of strings.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not a string list
     */
    public static List<String> getStringListParam(String path) {
        return CollectionConverters.asJava(org.galaxio.gatling.config.SimulationConfig.getStringListParam(requirePath(path)));
    }

    /**
     * Reads a required nested Typesafe Config block.
     *
     * @throws NullPointerException when {@code path} is null
     * @throws org.galaxio.gatling.config.SimulationConfigException when the path is missing or is not an object
     */
    public static Config getConfigParam(String path) {
        return org.galaxio.gatling.config.SimulationConfig.getConfigParam(requirePath(path));
    }

    /**
     * Reads an optional string parameter.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<String> getOptStringParam(String path) {
        return OptionConverters.toJava(org.galaxio.gatling.config.SimulationConfig.getOptStringParam(requirePath(path)));
    }

    /**
     * Reads an optional integer parameter.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<Integer> getOptIntParam(String path) {
        scala.Option<Object> value = org.galaxio.gatling.config.SimulationConfig.getOptIntParam(requirePath(path));
        return value.isDefined() ? Optional.of((Integer) value.get()) : Optional.empty();
    }

    /**
     * Reads an optional double parameter.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<Double> getOptDoubleParam(String path) {
        scala.Option<Object> value = org.galaxio.gatling.config.SimulationConfig.getOptDoubleParam(requirePath(path));
        return value.isDefined() ? Optional.of((Double) value.get()) : Optional.empty();
    }

    /**
     * Reads an optional duration parameter.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<Duration> getOptDurationParam(String path) {
        scala.Option<scala.concurrent.duration.FiniteDuration> value =
                org.galaxio.gatling.config.SimulationConfig.getOptDurationParam(requirePath(path));
        return value.isDefined() ? Optional.of(toJava(value.get())) : Optional.empty();
    }

    /**
     * Reads an optional boolean parameter.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<Boolean> getOptBooleanParam(String path) {
        scala.Option<Object> value = org.galaxio.gatling.config.SimulationConfig.getOptBooleanParam(requirePath(path));
        return value.isDefined() ? Optional.of((Boolean) value.get()) : Optional.empty();
    }

    /**
     * Reads an optional list of strings.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<List<String>> getOptStringListParam(String path) {
        scala.Option<scala.collection.immutable.List<String>> value =
                org.galaxio.gatling.config.SimulationConfig.getOptStringListParam(requirePath(path));
        return value.isDefined() ? Optional.of(CollectionConverters.asJava(value.get())) : Optional.empty();
    }

    /**
     * Reads an optional nested Typesafe Config block.
     *
     * @throws NullPointerException when {@code path} is null
     */
    public static Optional<Config> getOptConfigParam(String path) {
        return OptionConverters.toJava(org.galaxio.gatling.config.SimulationConfig.getOptConfigParam(requirePath(path)));
    }

    /** Returns the base HTTP URL used by simulations. */
    public static String baseUrl() {
        return org.galaxio.gatling.config.SimulationConfig.baseUrl();
    }

    /** Returns the base authentication URL used by simulations. */
    public static String baseAuthUrl() {
        return org.galaxio.gatling.config.SimulationConfig.baseAuthUrl();
    }

    /** Returns the base WebSocket URL used by simulations. */
    public static String wsBaseUrl() {
        return org.galaxio.gatling.config.SimulationConfig.wsBaseUrl();
    }

    /** Returns the number of workload stages. Must be greater than zero. */
    public static Integer stagesNumber() {
        return org.galaxio.gatling.config.SimulationConfig.stagesNumber();
    }

    /** Returns the ramp duration. Must be zero or greater. */
    public static Duration rampDuration() {
        return toJava(org.galaxio.gatling.config.SimulationConfig.rampDuration());
    }

    /** Returns the stage duration. Must be greater than zero. */
    public static Duration stageDuration() {
        return toJava(org.galaxio.gatling.config.SimulationConfig.stageDuration());
    }

    /** Returns the maximum simulation duration. Must be greater than zero. */
    public static Duration testDuration() {
        return toJava(org.galaxio.gatling.config.SimulationConfig.testDuration());
    }

    /** Returns the target request intensity converted to requests per second. */
    public static Double intensity() {
        return org.galaxio.gatling.config.SimulationConfig.intensity();
    }

    private static String requirePath(String path) {
        return Objects.requireNonNull(path, "path cannot be null");
    }
}
