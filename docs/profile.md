# Profile

[← Back to README](../README.md)

Load test profiles from HOCON or YAML files. Common traits to create profiles for any protocol; HTTP profile as a built-in example.

## Import

Common base import (covers both `ProfileBuilder` and `ProfileBuilderNew`):

```scala
import org.galaxio.gatling.profile._
```

The legacy v1 (PureConfig) path needs two more imports — `org.galaxio.gatling.profile.http._` (brings `HttpProfileConfig` and the required `ConfigReader[HttpMethodConfig]` implicit into scope) and `pureconfig.generic.auto._`. They are shown in its example below.

## Legacy profile (v1 HOCON/YAML)

HOCON:

```hocon
{
  name: test-profile
  profile: [
    {
      name: request-a
      url: "http://test.url"
      probability: 50.0
      method: get
    },
    {
      name: request-b
      url: "http://test.url"
      probability: 50.0
      method: post
      body: "{\"a\": \"1\"}"
    }
  ]
}
```

YAML:

```yaml
name: test-profile
profile:
  - name: request-a
    url: "http://test.url"
    probability: 50
    method: get
  - name: request-b
    url: "http://test.url"
    probability: 50
    method: post
    body: "{\"a\": \"1\"}"
```

Scala simulation:

```scala
import org.galaxio.gatling.profile._
import org.galaxio.gatling.profile.http._
import pureconfig.generic.auto._

class test extends Simulation {
  val profileConfigName = "profile.yml"
  val someTestPlan = constantUsersPerSec(intensity) during stageDuration
  val httpProtocol = http.baseUrl(baseUrl)
  val config: HttpProfileConfig = new ProfileBuilder[HttpProfileConfig].buildFromYaml(profileConfigName)
  val scn: ScenarioBuilder = config.toRandomScenario

  setUp(
    scn.inject(
      atOnceUsers(10)
    ).protocols(httpProtocol)
  ).maxDuration(10)
}
```

## New-style profile (v2 YAML)

```yaml
apiVersion: link.ru/v1alpha1
kind: PerformanceTestProfiles
metadata:
  name: performance-test-profile
  description: performance test profile
spec:
  profiles:
    - name: maxPerf
      period: 10.05.2022 - 20.05.2022
      protocol: http
      profile:
        - request: request-1
          intensity: 100 rph
          groups: ["Group1"]
          params:
            method: POST
            path: /test/a
            headers:
              - 'Content-Type: application/json'
              - 'Connection: keep-alive'
            body: '{"a": "b"}'
        - request: request-2
          intensity: 200 rph
          groups: ["Group1", "Group2"]
          params:
            method: GET
            path: /test/b
            body: '{"c": "d"}'
        - request: request-3
          intensity: 200 rph
          groups: [ "Group1", "Group2" ]
          params:
            method: GET
            path: /test/c
            body: '{"e": "f"}'
```

Optional fields: `groups`, `headers`, `body`. Missing required fields throw an exception.

Scala:

```scala
class Debug extends Simulation {
  val pathToProfile = "path/to/profile.yml"
  val scn = ProfileBuilderNew.buildFromYaml(pathToProfile).selectProfile("maxPerf").toRandomScenario

  setUp(
    scn.inject(
      atOnceUsers(10)
    ).protocols(httpProtocol)
  ).maxDuration(10)
}
```

Java:

```java
import org.galaxio.gatling.javaapi.profile.ProfileBuilderNew;

public class Debug extends Simulation {

  public static ScenarioBuilder scn = ProfileBuilderNew
          .buildFromYaml("path/to/profile.yml")
          .selectProfile("maxPerf")
          .toRandomScenario();

  {
    setUp(
            scn.injectOpen(atOnceUsers(1))
    ).protocols(httpProtocol);
  }
}
```

Kotlin:

```kotlin
import org.galaxio.gatling.javaapi.profile.ProfileBuilderNew

class Debug : Simulation() {
  val scn: ScenarioBuilder = ProfileBuilderNew
    .buildFromYaml("path/to/profile.yml")
    .selectProfile("maxPerf")
    .toRandomScenario()

  init {
    setUp(
      scn.injectOpen(atOnceUsers(1)),
    ).protocols(httpProtocol)
  }
}
```
