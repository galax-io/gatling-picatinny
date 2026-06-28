# Templates

[← Back to README](../README.md)

DSL for building JSON and XML request bodies with Gatling EL expression support, plus file-based template loading.

String values are automatically escaped for JSON (`"`, `\`, newlines) and XML (`&`, `<`, `>`). `jsonBody` sets `Content-Type: application/json`, `xmlBody` sets `Content-Type: application/xml`.

## DSL operator reference

| Operator               | Description                      | JSON output            |
|------------------------|----------------------------------|------------------------|
| `"field" - "value"`    | Literal string                   | `"field": "value"`     |
| `"field" - 42`         | Literal number/boolean           | `"field": 42`          |
| `"field" ~ "var"`      | Session variable reference       | `"field": "#{var}"`    |
| `"field"` (implicit)   | Session variable with same name  | `"field": "#{field}"`  |
| `"field" > (1, 2, 3)`  | Array                            | `"field": [1,2,3]`     |
| `"field" - ("a" - 1)`  | Nested object                    | `"field": {"a": 1}`    |
| `"field" - nullVal`    | Null value                       | `"field": null`        |

## jsonBody

Scala:

```scala
import org.galaxio.gatling.templates.HttpBodyExt._
import org.galaxio.gatling.templates.Syntax._

class SampleScenario {
  val sendJson: ScenarioBuilder =
    scenario("Post some")
      .exec(
        http("PostData")
          .post(url)
          .jsonBody(
            "id" - 23,
            "name",                       // session variable #{name}
            "project" - (
              "id" ~ "projectId",         // session variable #{projectId}
              "name" - "Super Project",
              "sub" > (1, 2, 3, 4, 5, 6),
            ),
            "deleted" - nullVal,          // null value
          )
      )
}
```

Java:

```java
import static org.galaxio.gatling.javaapi.TemplateSyntax.*;

String json = makeJson(
    field("id", 23),
    sessionVar("name", "name"),
    fieldObj("project",
        sessionVar("id", "projectId"),
        field("name", "Super Project"),
        fieldArr("sub", 1, 2, 3, 4, 5, 6)
    ),
    fieldNull("deleted")
);

// Use with Gatling: .body(StringBody(json)).asJson
```

Kotlin:

```kotlin
import org.galaxio.gatling.javaapi.TemplateSyntax.*

val json = makeJson(
    field("id", 23),
    sessionVar("name", "name"),
    fieldObj("project",
        sessionVar("id", "projectId"),
        field("name", "Super Project"),
        fieldArr("sub", 1, 2, 3, 4, 5, 6)
    ),
    fieldNull("deleted")
)
```

Output:

Real output is a single compact line (no spaces after commas):

```json
{"id": 23,"name": "#{name}","project": {"id": "#{projectId}","name": "Super Project","sub": [1,2,3,4,5,6]},"deleted": null}
```

## xmlBody

Scala:

```scala
import org.galaxio.gatling.templates.HttpBodyExt._
import org.galaxio.gatling.templates.Syntax._

http("PostXml")
  .post(url)
  .xmlBody(
    "id" - 23,
    "name" ~ "userName",
    "tags" > ("alpha", "beta"),
  )
```

Java:

```java
import static org.galaxio.gatling.javaapi.TemplateSyntax.*;

String xml = makeXml(
    field("id", 23),
    sessionVar("name", "userName"),
    fieldArr("tags", "alpha", "beta")
);
```

Produces: `<id>23</id><name>#{userName}</name><tags><item>alpha</item><item>beta</item></tags>`

## postTemplate

Loads template files from `resources/templates` and sends them as POST request bodies. Templates support [Gatling EL expressions](https://gatling.io/docs/gatling/reference/current/session/expression_el/). Lazily loaded on first access.

```bash
$ tree resources/
.
├── gatling.conf
├── logback.xml
├── simulation.conf
└── templates
    └── example_template1.json
    └── example_template2.json
```

Scala:

```scala
class SampleScenario extends Templates {
  val sendTemplates: ScenarioBuilder =
    scenario("Templates scenario")
      .exec(postTemplate("example_template1", "/post_route"))
      .exec(postTemplate("example_template2", "/post_route"))
}
```

Sends 2 POST requests: one with body from `example_template1.json`, second from `example_template2.json` to `$baseUrl/post_route`. Unknown template name throws `NoSuchElementException` listing available templates.
