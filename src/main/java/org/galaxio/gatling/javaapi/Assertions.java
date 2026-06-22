package org.galaxio.gatling.javaapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.gatling.javaapi.core.Assertion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static io.gatling.javaapi.core.CoreDsl.*;

public final class Assertions {

    private record RecordNFR(String key, HashMap<String, String> value) {}

    private record NFR(List<RecordNFR> nfr) {}

    private Assertions() {

    }

    private static final Logger log = LoggerFactory.getLogger(Assertions.class);

    // Built once at class init (modules registered once) — not per getNfr() call. Reflection-heavy module scanning must not
    // repeat on every load.
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private static NFR getNfr(String path) throws AssertionBuilderException {
        try {
            return MAPPER.readValue(new File(path), NFR.class);
        } catch (MismatchedInputException e) {
            throw new AssertionBuilderException("Incorrect file content " + path, e);
        } catch (FileNotFoundException e) {
            throw new AssertionBuilderException("NFR File not found " + path, e);
        } catch (Exception e) {
            throw new AssertionBuilderException("Unknown error " + path, e);
        }
    }

    private static String[] getGroupAndRequest(String key) {
        return key.split(" / ");
    }

    /** Checked integer parse for response-time thresholds (milliseconds). Names the offending NFR metric key and value
     * instead of an opaque {@link NumberFormatException}. No trimming — kept strict to match the Scala builder. */
    private static int parseIntThreshold(String metricKey, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new AssertionBuilderException(
                    "NFR assertion '" + metricKey + "': value '" + value + "' is not a valid number", e);
        }
    }

    /** Checked decimal parse for the error-rate threshold (a percentage — fractional values such as {@code 5.5} are valid). */
    private static double parseDoubleThreshold(String metricKey, String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new AssertionBuilderException(
                    "NFR assertion '" + metricKey + "': value '" + value + "' is not a valid number", e);
        }
    }

    private static List<Assertion> buildAssertion(RecordNFR record) {
        return switch (record.key()) {
            case "Процент ошибок" -> buildErrorAssertion(record);
            case "99 перцентиль времени выполнения" -> buildPercentileAssertion(record, 99.0);
            case "95 перцентиль времени выполнения" -> buildPercentileAssertion(record, 95.0);
            case "75 перцентиль времени выполнения" -> buildPercentileAssertion(record, 75.0);
            case "50 перцентиль времени выполнения" -> buildPercentileAssertion(record, 50.0);
            case "Максимальное время выполнения" -> buildMaxResponseTimeAssertion(record);
            default -> {
                log.warn("Unknown NFR assertion key '{}' — skipped", record.key());
                yield new LinkedList<>();
            }
        };
    }

    private static List<Assertion> buildPercentileAssertion(RecordNFR record, Double percentile) {
        List<Assertion> assertionList = new LinkedList<>();

        for (Map.Entry<String, String> entry : record.value().entrySet()) {
            String key = entry.getKey();
            int value = parseIntThreshold(record.key(), entry.getValue());

            assertionList.add(Objects.equals(key, "all")
                    ? global().responseTime().percentile(percentile).lt(value)
                    : details(getGroupAndRequest(key)).responseTime().percentile(percentile).lt(value));
        }

        return assertionList;
    }

    private static List<Assertion> buildErrorAssertion(RecordNFR record) {
        List<Assertion> assertionList = new LinkedList<>();

        for (Map.Entry<String, String> entry : record.value().entrySet()) {
            String key = entry.getKey();
            double value = parseDoubleThreshold(record.key(), entry.getValue());

            assertionList.add(Objects.equals(key, "all")
                    ? global().failedRequests().percent().lt(value)
                    : details(getGroupAndRequest(key)).failedRequests().percent().lt(value));
        }
        return assertionList;
    }

    private static List<Assertion> buildMaxResponseTimeAssertion(RecordNFR record) {
        List<Assertion> assertionList = new LinkedList<>();

        for (Map.Entry<String, String> entry : record.value().entrySet()) {
            String key = entry.getKey();
            int value = parseIntThreshold(record.key(), entry.getValue());

            assertionList.add(Objects.equals(key, "all")
                    ? global().responseTime().max().lt(value)
                    : details(getGroupAndRequest(key)).responseTime().max().lt(value));
        }
        return assertionList;
    }

    // NOTE: this mirrors the canonical, unit-tested Scala org.galaxio.gatling.assertions.AssertionsBuilder. It cannot delegate
    // to it because the Java io.gatling.javaapi.core.Assertion wrap constructor is package-private (no public way to convert a
    // core Assertion into a Java one), so the facade builds via the Java DSL (global()/details()). Keep the NFR-key mapping
    // here in sync with AssertionsBuilder.scala.

    /**
     * @deprecated NFR-YAML assertion loading is deprecated and will be replaced by new assertions functionality in a future
     *     release. It still works for now; watch the changelog.
     */
    @Deprecated(since = "1.18.0")
    public static List<Assertion> assertionFromYaml(String path) {

        List<Assertion> assertionList = new LinkedList<>();

        NFR nfr = getNfr(path);
        List<RecordNFR> recordNFRList = nfr.nfr();
        recordNFRList.forEach(recordNFR -> assertionList.addAll(buildAssertion(recordNFR)));

        return assertionList;
    }
}
