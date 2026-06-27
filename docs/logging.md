# Logging & Secret Masking

## Contents

- [Recommended configuration](#recommended-configuration)
- [Overriding / suppressing output](#overriding--suppressing-output)
- [Secret masking](#secret-masking)
  - [Adding project-specific sensitive keys](#adding-project-specific-sensitive-keys)
- [Migration — upgrading to 1.23.0](#migration--upgrading-to-1230)
- [Startup banner & diagnostics (usage)](#startup-banner--diagnostics-usage)

Picatinny logs through **SLF4J** (via `scala-logging`). It deliberately ships **no** `logback.xml` on its
main/compile classpath — a published library must not impose a logging backend or auto-discovered config on
consumers (it would collide with your own `logback.xml`, "Resource [logback.xml] occurs multiple times on the
classpath", with non-deterministic first-match wins). You stay in full control of logging configuration.

## Recommended configuration

Picatinny's startup banner and diagnostics are emitted through SLF4J as a **single log event** per block, under the
logger category `org.galaxio.gatling.diagnostics`. To keep the ASCII banner aligned (no per-line timestamp/level
prefix) while keeping ordinary logs prefixed, bind that category to a bare `%msg%n` appender with
`additivity="false"`.

Copy this into your project's `src/test/resources/logback.xml` (Gatling simulations run in the test scope):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%-5level] %logger{20} - %msg%n%rEx</pattern>
        </encoder>
    </appender>

    <!-- Banner/diagnostics: prefix-free, single event, suppressible by level. -->
    <appender name="BANNER" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%msg%n</pattern>
        </encoder>
    </appender>

    <logger name="org.galaxio.gatling.diagnostics" level="INFO" additivity="false">
        <appender-ref ref="BANNER"/>
    </logger>

    <root level="ERROR">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

The canonical, runnable copy of this file lives in the example overlay:
[`examples/scala-sbt-example/src/test/resources/logback.xml`](../examples/scala-sbt-example/src/test/resources/logback.xml).

## Overriding / suppressing output

- **Turn the banner / diagnostics on or off — the canonical way:** the Picatinny flags
  `picatinny.startup.banner.enabled` (default `true`) and `picatinny.diagnostics.enabled` (default `false`), set in
  `simulation.conf` or overridden with `-Dpicatinny.startup.banner.enabled=false`. Use these — not the logging level — to
  toggle output.
- **Backend-level control (only if you don't own the simulation config):** set the `org.galaxio.gatling.diagnostics`
  logger level (`INFO` shows, `OFF`/`WARN` hides), or point at another file with `-Dlogback.configurationFile=…`. A
  `logback-test.xml` on the test classpath wins over `logback.xml`.

## Secret masking

Picatinny redacts secrets at every log/print/exception sink. A value is masked when its key's **last path segment**
matches a sensitive term (whole-word / suffix-run match, not raw substring): `password`, `secret`, `token`,
`apiKey`, `clientSecret`, `bearer`, `authorization`, `passphrase`, `privateKey`, `accessKey`, `credential`, and
their case/`_`/`-` variants. Non-secret identifiers like `roleId` and benign compounds like `tokenBucketSize` stay
visible. JWT signing keys never print their material (`StringSecret(******)`), and URLs have their `user:password@`
userinfo stripped before logging.

### Adding project-specific sensitive keys

The term list is the built-in floor **merged** with your own (you can only ADD, never remove a built-in):

```hocon
picatinny.redaction {
  additionalSensitiveKeys = ["myCorpToken", "internalPass"]
  replacement = "******"   # optional; defaults to ******
}
```

## Migration — upgrading to 1.23.0

1.23.0 hardens secret handling. These are the consumer-visible behavior changes and how to adapt.

### Banner & diagnostics now go through SLF4J (not `stdout`)

`Utility.banner(...)` / `Utility.diagnostics()` previously wrote with `println`. They now emit a single
`logger.info` event under `org.galaxio.gatling.diagnostics`.

- **If you saw the banner on `stdout` with no logging config**: add the [recommended configuration](#recommended-configuration)
  so the banner renders (prefix-free) again. Without any logging config it may be filtered by your backend's default level.
- **If you grep/parse `stdout` in CI for the banner**: switch to capturing the logging output, or point the
  `org.galaxio.gatling.diagnostics` category at a file/console appender.
- The existing `picatinny.startup.banner.enabled` / `picatinny.diagnostics.enabled` flags still gate emission — no change.

### The library no longer ships a `logback.xml`

If you relied (knowingly or not) on a logging config arriving transitively from picatinny, **add your own** (copy the
[recommended snippet](#recommended-configuration)). This removes the `Resource [logback.xml] occurs multiple times on the
classpath` warning when your app already has one.

### Masking is now whole-word, not substring

Old behavior masked any key whose path **contained** a sensitive token. New behavior matches on the key's **last path
segment** by whole word / compound run. Net differences you may notice in logs:

| Key | Before (≤1.22) | After (1.23.0) |
|-----|----------------|----------------|
| `tokenBucketSize`, `apiKeyboard`, `secretariat` | masked (false positive) | **visible** |
| `passwordHash`, `tokenValue`, `secret_id` | visible (missed) | **masked** |
| `roleId`, `roleIdPrefix` | visible | visible (unchanged) |
| `db.password`, `apiKey`, `clientSecret` | masked | masked (unchanged) |

If a project-specific key now logs in clear (or you want a previously-visible one masked), add it under
[`picatinny.redaction.additionalSensitiveKeys`](#adding-project-specific-sensitive-keys) — the built-in floor can only
be extended, never weakened.

### New (optional) config surface

`picatinny.redaction.{additionalSensitiveKeys, replacement}` is additive and optional — no action needed unless you want
to extend masking. Absent block = built-in defaults.

## Startup banner & diagnostics (usage)

Picatinny provides explicit utility methods for startup output. Put them where it makes sense for your project: in a
simulation class, a shared package object, or another project bootstrap point. Output goes through SLF4J under
`org.galaxio.gatling.diagnostics` — see [Recommended configuration](#recommended-configuration) to render it.

Default behavior:

```text
picatinny.startup.banner.enabled = true
picatinny.diagnostics.enabled = false
```

Disable the banner for quiet runs:

```bash
-Dpicatinny.startup.banner.enabled=false
```

Enable JVM/runtime diagnostics when you need extra debug information:

```bash
-Dpicatinny.diagnostics.enabled=true
```

Pass the same Gatling injection steps to `Utility.banner(...)` that you pass to `inject`/`injectOpen`. The banner parses
the actual injector objects and renders the effective workload. If no injectors are passed, it falls back to
`simulation.conf`.

Example `simulation.conf`:

```hocon
baseUrl = "http://localhost"
intensity = "60 rpm"
stagesNumber = 2
rampDuration = 1 minute
stageDuration = 5 minutes

picatinny.startup.banner.enabled = true
picatinny.diagnostics.enabled = false
```

Scala usage:

```scala
import io.gatling.core.Predef._
import org.galaxio.gatling.config.SimulationConfig._
import org.galaxio.gatling.utils.Utility

class Stability extends Simulation {
  val injectionProfile = (
    rampUsersPerSec(0).to(intensity).during(rampDuration),
    constantUsersPerSec(intensity).during(stageDuration),
  )

  Utility.banner(injectionProfile)
  Utility.diagnostics()

  setUp(
    scn.inject(injectionProfile._1, injectionProfile._2),
  ).protocols(http.baseUrl(baseUrl))
}
```

Java usage:

```java
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.Simulation;
import org.galaxio.gatling.javaapi.Utility;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static org.galaxio.gatling.javaapi.SimulationConfig.*;

public final class Stability extends Simulation {
    {
        OpenInjectionStep[] injectionProfile = {
            rampUsersPerSec(0).to(intensity()).during(rampDuration()),
            constantUsersPerSec(intensity()).during(stageDuration())
        };

        Utility.banner(injectionProfile);
        Utility.diagnostics();

        setUp(scn.injectOpen(injectionProfile));
    }
}
```

Kotlin usage:

```kotlin
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import io.gatling.javaapi.core.OpenInjectionStep
import io.gatling.javaapi.core.Simulation
import org.galaxio.gatling.javaapi.Utility
import org.galaxio.gatling.javaapi.SimulationConfig.*

class Stability : Simulation() {
    init {
        val injectionProfile = arrayOf<OpenInjectionStep>(
            rampUsersPerSec(0.0).to(intensity()).during(rampDuration()),
            constantUsersPerSec(intensity()).during(stageDuration()),
        )

        Utility.banner(*injectionProfile)
        Utility.diagnostics()

        setUp(scn.injectOpen(*injectionProfile))
    }
}
```

Startup output:

```text
================================================================================
 Picatinny Gatling Run
================================================================================
 Simulation   : Stability
 Base URL     : http://localhost

 Workload
   intensity      : 1.00 rps
   profile        : provided-open-injection
   ramp duration  : 1m
   stage duration : 5m
   total duration : 6m

 Timeline
   00:00 - 01:00  ramp   0.00 -> 1.00 rps
   01:00 - 06:00  plateau  1.00 rps

 ASCII preview
   rps
    1.00 |           //___________________________________________________________
    0.89 |          /
    0.78 |         /
    0.67 |        /
    0.56 |      //
    0.44 |     /
    0.33 |    /
    0.22 |   /
    0.11 | //
    0.00 |/
         +------------------------------------------------------------------------
          00:00                                                            06:00
================================================================================
```

