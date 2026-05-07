package org.galaxio.gatling.javaapi.assertions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.galaxio.gatling.javaapi.Assertions.assertionFromYaml;

import org.galaxio.gatling.javaapi.AssertionBuilderException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaAssertionFromYamlTest {

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
