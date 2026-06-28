# Assertions

[← Back to README](../README.md)

> **Deprecated since v1.18.0.** `assertionFromYaml` and `AssertionBuilderException` are slated for removal no earlier than
> the next major release (**2.0.0+**, [milestone](https://github.com/galax-io/gatling-picatinny/milestone/27)) as part of a broader assertions API redesign.
> The API still works today — no action required now. Watch the CHANGELOG for the replacement.
> See [Migration Guide → Deprecations](migration.md#deprecations) for details.

Load NFR (non-functional requirements) assertion configs from YAML files.

## Import

```scala
import org.galaxio.gatling.assertions.AssertionsBuilder.assertionFromYaml
```

Java / Kotlin:

```java
import static org.galaxio.gatling.javaapi.Assertions.assertionFromYaml;
```

## Supported requirements

| Requirement | YAML key |
|---|---|
| 99th percentile of responseTime | `99 перцентиль времени выполнения` |
| 95th percentile of responseTime | `95 перцентиль времени выполнения` |
| 75th percentile of responseTime | `75 перцентиль времени выполнения` |
| 50th percentile of responseTime | `50 перцентиль времени выполнения` |
| Percent of failed requests | `Процент ошибок` |
| Maximum responseTime | `Максимальное время выполнения` |

## NFR YAML format

```yaml
nfr:
  - key: '99 перцентиль времени выполнения'
    value:
      GET /: '500'
      MyGroup / MyRequest: '900'
      request_1: '700'
      all: '1000'
  - key: 'Процент ошибок'
    value:
      all: '5'
  - key: 'Максимальное время выполнения'
    value:
      GET /: '1000'
      all: '2000'
```

## Usage

Scala:

```scala
class test extends Simulation {

  setUp(
    scn.inject(
      atOnceUsers(10)
    ).protocols(httpProtocol)
  ).maxDuration(10)
    .assertions(assertionFromYaml("src/test/resources/nfr.yml"))
}
```

Java:

```java
import static org.galaxio.gatling.javaapi.Assertions.assertionFromYaml;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;

import io.gatling.javaapi.core.Simulation;

public class TestSimulation extends Simulation {
  public TestSimulation() {
    setUp(
      scn.injectOpen(
        atOnceUsers(10)
      ).protocols(httpProtocol)
    ).maxDuration(10)
     .assertions(assertionFromYaml("src/test/resources/nfr.yml"));
  }
}
```

Kotlin:

```kotlin
import org.galaxio.gatling.javaapi.Assertions.assertionFromYaml
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.core.CoreDsl.atOnceUsers

class TestSimulation : Simulation() {
  init {
    setUp(
      scn.injectOpen(
        atOnceUsers(10)
      ).protocols(httpProtocol)
    ).maxDuration(10)
      .assertions(assertionFromYaml("src/test/resources/nfr.yml"))
  }
}
```
