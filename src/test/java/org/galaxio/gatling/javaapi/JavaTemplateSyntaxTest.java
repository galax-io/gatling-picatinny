package org.galaxio.gatling.javaapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.galaxio.gatling.javaapi.TemplateSyntax.*;

import org.junit.jupiter.api.Test;

class JavaTemplateSyntaxTest {

    @Test
    void shouldBuildJsonWithStringField() {
        String json = makeJson(field("name", "John"));
        assertThat(json).isEqualTo("{\"name\": \"John\"}");
    }

    @Test
    void shouldBuildJsonWithIntField() {
        String json = makeJson(field("count", 42));
        assertThat(json).isEqualTo("{\"count\": 42}");
    }

    @Test
    void shouldBuildJsonWithDoubleField() {
        String json = makeJson(field("score", 3.14));
        assertThat(json).isEqualTo("{\"score\": 3.14}");
    }

    @Test
    void shouldBuildJsonWithBooleanField() {
        String json = makeJson(field("active", true));
        assertThat(json).isEqualTo("{\"active\": true}");
    }

    @Test
    void shouldBuildJsonWithSessionVar() {
        String json = makeJson(sessionVar("userId", "uid"));
        assertThat(json).isEqualTo("{\"userId\": \"#{uid}\"}");
    }

    @Test
    void shouldBuildJsonWithNestedObject() {
        String json = makeJson(
                fieldObj("user",
                        field("name", "John"),
                        field("age", 30)
                )
        );
        assertThat(json).isEqualTo("{\"user\": {\"name\": \"John\",\"age\": 30}}");
    }

    @Test
    void shouldBuildJsonWithArray() {
        String json = makeJson(
                fieldArr("tags", "a", "b", "c")
        );
        assertThat(json).isEqualTo("{\"tags\": [\"a\",\"b\",\"c\"]}");
    }

    @Test
    void shouldBuildJsonWithNullField() {
        String json = makeJson(fieldNull("empty"));
        assertThat(json).isEqualTo("{\"empty\": null}");
    }

    @Test
    void shouldBuildJsonWithMultipleFields() {
        String json = makeJson(
                field("id", 1),
                field("name", "test"),
                sessionVar("token", "authToken"),
                fieldNull("deleted")
        );
        assertThat(json).isEqualTo("{\"id\": 1,\"name\": \"test\",\"token\": \"#{authToken}\",\"deleted\": null}");
    }

    @Test
    void shouldBuildXmlWithFields() {
        String xml = makeXml(
                field("name", "John"),
                field("age", 30)
        );
        assertThat(xml).isEqualTo("<name>John</name><age>30</age>");
    }

    @Test
    void shouldReturnEmptyJson() {
        assertThat(emptyJson()).isEqualTo("{}");
    }

    @Test
    void shouldReturnEmptyArr() {
        assertThat(emptyArr()).isEqualTo("[]");
    }

    @Test
    void shouldEscapeSpecialCharsInJson() {
        String json = makeJson(field("msg", "say \"hello\""));
        assertThat(json).isEqualTo("{\"msg\": \"say \\\"hello\\\"\"}");
    }

    @Test
    void shouldEscapeSpecialCharsInXml() {
        String xml = makeXml(field("data", "<b>bold & cool</b>"));
        assertThat(xml).isEqualTo("<data>&lt;b&gt;bold &amp; cool&lt;/b&gt;</data>");
    }
}
