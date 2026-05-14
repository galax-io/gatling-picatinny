package org.galaxio.gatling.javaapi;

import org.galaxio.gatling.templates.Syntax;
import org.galaxio.gatling.templates.Syntax$;

import java.util.Arrays;

import static scala.jdk.javaapi.CollectionConverters.asScala;

/**
 * Java API for the Gatling Picatinny templates DSL.
 *
 * <p>Provides static factory methods for building JSON and XML request body strings
 * with Gatling EL expression support.
 *
 * <pre>{@code
 * import static org.galaxio.gatling.javaapi.TemplateSyntax.*;
 *
 * String json = makeJson(
 *     field("id", 42),
 *     field("name", "John"),
 *     sessionVar("userId", "uid"),
 *     fieldObj("address",
 *         field("city", "Moscow"),
 *         field("zip", "101000")
 *     ),
 *     fieldArr("tags", "alpha", "beta")
 * );
 * }</pre>
 */
public final class TemplateSyntax {

    private TemplateSyntax() {
    }

    /**
     * Creates a field with a literal string value.
     *
     * @param name  field name
     * @param value string value
     * @return a Field with RawValString
     */
    public static Syntax.Field field(String name, String value) {
        return new Syntax.Field(name, new Syntax.RawValString(value));
    }

    /**
     * Creates a field with an integer value.
     *
     * @param name  field name
     * @param value integer value
     * @return a Field with RawValGen
     */
    public static Syntax.Field field(String name, int value) {
        return new Syntax.Field(name, new Syntax.RawValGen<>(value));
    }

    /**
     * Creates a field with a long value.
     *
     * @param name  field name
     * @param value long value
     * @return a Field with RawValGen
     */
    public static Syntax.Field field(String name, long value) {
        return new Syntax.Field(name, new Syntax.RawValGen<>(value));
    }

    /**
     * Creates a field with a double value.
     *
     * @param name  field name
     * @param value double value
     * @return a Field with RawValGen
     */
    public static Syntax.Field field(String name, double value) {
        return new Syntax.Field(name, new Syntax.RawValGen<>(value));
    }

    /**
     * Creates a field with a boolean value.
     *
     * @param name  field name
     * @param value boolean value
     * @return a Field with RawValGen
     */
    public static Syntax.Field field(String name, boolean value) {
        return new Syntax.Field(name, new Syntax.RawValGen<>(value));
    }

    /**
     * Creates a field referencing a Gatling session variable.
     * Rendered as {@code "name": "#{varName}"} in JSON.
     *
     * @param name    field name
     * @param varName session variable name
     * @return a Field with InterpolateStrVal
     */
    public static Syntax.Field sessionVar(String name, String varName) {
        return new Syntax.Field(name, new Syntax.InterpolateStrVal(varName));
    }

    /**
     * Creates a field with a nested object value.
     *
     * @param name   field name
     * @param fields nested fields
     * @return a Field with ObjectVal
     */
    public static Syntax.Field fieldObj(String name, Syntax.Field... fields) {
        return new Syntax.Field(name, new Syntax.ObjectVal(
                scala.collection.immutable.List.from(asScala(Arrays.asList(fields)))
        ));
    }

    /**
     * Creates a field with an array of values.
     *
     * @param name   field name
     * @param values array elements
     * @return a Field with ArrayVal
     */
    public static Syntax.Field fieldArr(String name, Object... values) {
        return new Syntax.Field(name, Syntax$.MODULE$.arr(
                scala.collection.immutable.ArraySeq.unsafeWrapArray(values)
        ));
    }

    /**
     * Creates a field with a null value.
     * Rendered as {@code "name": null} in JSON, {@code <name/>} in XML.
     *
     * @param name field name
     * @return a Field with NullVal
     */
    public static Syntax.Field fieldNull(String name) {
        return new Syntax.Field(name, Syntax.NullVal$.MODULE$);
    }

    /**
     * Builds a JSON object string from the given fields.
     *
     * @param fields the fields to serialize
     * @return JSON string, e.g. {@code {"id": 1,"name": "test"}}
     */
    public static String makeJson(Syntax.Field... fields) {
        return Syntax$.MODULE$.makeJson(
                scala.collection.immutable.List.from(asScala(Arrays.asList(fields)))
        );
    }

    /**
     * Builds an XML string from the given fields.
     *
     * @param fields the fields to serialize
     * @return XML string, e.g. {@code <id>1</id><name>test</name>}
     */
    public static String makeXml(Syntax.Field... fields) {
        return Syntax$.MODULE$.makeXml(
                scala.collection.immutable.List.from(asScala(Arrays.asList(fields)))
        );
    }

    /**
     * Returns an empty JSON object string {@code {}}.
     */
    public static String emptyJson() {
        return Syntax$.MODULE$.emptyJson();
    }

    /**
     * Returns an empty JSON array string {@code []}.
     */
    public static String emptyArr() {
        return Syntax$.MODULE$.emptyArr();
    }
}
