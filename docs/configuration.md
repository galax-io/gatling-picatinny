# Configuration

[← Back to README](../README.md)

## SimulationConfig

The only class that you need from this module is `SimulationConfig`. It could be used to attach some default variables
such as `intensity`, `baseUrl`, `baseAuthUrl` and some others to your scripts. Also, it provides functions to get custom
variables from config.

Import:

Scala example:

```scala
import org.galaxio.gatling.config.SimulationConfig._
```

Java example:

```java
import static org.galaxio.gatling.javaapi.SimulationConfig.*;
```

Kotlin example:

```kotlin
import org.galaxio.gatling.javaapi.SimulationConfig.*
```

Using default variables:

Scala example:

```scala
import org.galaxio.gatling.config.SimulationConfig._

val testPlan: Seq[OpenInjectionStep] = List(
  rampUsersPerSec(0).to(intensity).during(rampDuration),
  constantUsersPerSec(intensity).during(stageDuration)
)
```

Java example:

```java
import static org.galaxio.gatling.javaapi.SimulationConfig.*;

public class Example {
  {
    SomeScenario.scn.injectOpen(
      incrementUsersPerSec(intensity() / stagesNumber())
        .times(stagesNumber())
        .eachLevelLasting(stageDuration())
        .separatedByRampsLasting(rampDuration())
        .startingFrom(0.0)
    );
  }
}
```

Kotlin example:

```kotlin
import org.galaxio.gatling.javaapi.SimulationConfig.*

SomeScenario.scn.injectOpen(
  incrementUsersPerSec(intensity() / stagesNumber())
    .times(stagesNumber())
    .eachLevelLasting(stageDuration())
    .separatedByRampsLasting(rampDuration())
    .startingFrom(0.0)
)
```

Using functions to get custom variable:

*simulation.conf*

```hocon
stringVariable: "FOO",
intVariable: 1,
doubleVariable: 3.1415,
duration: {
    durationVariable: 3600s
}
booleanVariable: true
stringListVariable: ["foo", "bar"]
client {
  timeout: 10 seconds
}
```

Scala example:

```scala
import org.galaxio.gatling.config.SimulationConfig._

val stringVariable = getStringParam("stringVariable")
val intVariable = getIntParam("intVariable")
val doubleVariable = getDoubleParam("doubleVariable")
val durationVariable = getDurationParam("duration.durationVariable")
val booleanVariable = getBooleanParam("booleanVariable")
val stringListVariable = getStringListParam("stringListVariable")
val clientConfig = getConfigParam("client")

val optionalValue = getOptStringParam("optionalVariable")
```

Java example:

```java
import static org.galaxio.gatling.javaapi.SimulationConfig.*;

String stringVariable = getStringParam("stringVariable");
int intVariable = getIntParam("intVariable");
double doubleVariable = getDoubleParam("doubleVariable");
Duration durationVariable = getDurationParam("duration.durationVariable");
boolean booleanVariable = getBooleanParam("booleanVariable");
List<String> stringListVariable = getStringListParam("stringListVariable");
Config clientConfig = getConfigParam("client");

Optional<String> optionalValue = getOptStringParam("optionalVariable");
```

Kotlin example:

```kotlin
import org.galaxio.gatling.javaapi.SimulationConfig.*

val stringVariable = getStringParam("stringVariable")
val intVariable = getIntParam("intVariable")
val doubleVariable = getDoubleParam("doubleVariable")
val durationVariable = getDurationParam("duration.durationVariable")
val booleanVariable = getBooleanParam("booleanVariable")
val stringListVariable = getStringListParam("stringListVariable")
val clientConfig = getConfigParam("client")

val optionalValue = getOptStringParam("optionalVariable")
```

Required getters throw `SimulationConfigException` when a value is missing or has an invalid type. Optional getters return
`None` in Scala and `Optional.empty()` in Java/Kotlin when the path is not defined.

JVM system properties override values from `simulation.conf`, which is useful for CI and environment-specific runs:

```bash
sbt Gatling/test -DbaseUrl=https://test.example.org -Dintensity="120 rpm"
```

Workload defaults are validated when they are first read:

- `stagesNumber` must be greater than `0`
- `intensity` must resolve to a value greater than `0` rps
- `rampDuration` must be `0` or greater
- `stageDuration` and `testDuration` must be greater than `0`

Config values are logged when they are read. Keys whose last path segment is a sensitive term (`password`, `secret`,
`token`, `apiKey`, `clientSecret`, `bearer`, `authorization`, `passphrase`, `credential`, …) are masked as `******`.
Matching is whole-word (so `roleId`/`tokenBucketSize` stay visible), and you can add your own terms via
`picatinny.redaction.additionalSensitiveKeys`. See [docs/logging.md](logging.md) for the full behavior, the
recommended `logback.xml`, and the [1.23.0 migration notes](logging.md#migration--upgrading-to-1230).
