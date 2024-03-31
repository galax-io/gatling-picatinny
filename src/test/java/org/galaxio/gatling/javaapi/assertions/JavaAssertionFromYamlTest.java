package org.galaxio.gatling.javaapi.assertions;

import static org.galaxio.gatling.javaapi.Assertions.assertionFromYaml;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.galaxio.gatling.javaapi.AssertionBuilderException;

public class JavaAssertionFromYamlTest {

    @Test
    void incorrectFileContent() {
        AssertionBuilderException thrown = Assertions.assertThrows(AssertionBuilderException.class, () -> assertionFromYaml("src/test/resources/nfrInvalid.yml"));
        Assertions.assertEquals("Incorrect file content src/test/resources/nfrInvalid.yml", thrown.msg().trim());
    }

    @Test
    void fileNotFoundEmptyName() {

        AssertionBuilderException thrown = Assertions.assertThrows(AssertionBuilderException.class, () -> assertionFromYaml(""));
        Assertions.assertEquals("NFR File not found", thrown.msg().trim());
    }

    @Test
    void fileNotFound() {

        AssertionBuilderException thrown = Assertions.assertThrows(AssertionBuilderException.class, () -> assertionFromYaml("perf/nfr1.yml"));
        Assertions.assertEquals("NFR File not found perf/nfr1.yml", thrown.msg().trim());
    }
}
