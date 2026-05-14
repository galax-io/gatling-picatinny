# Gatling Picatinny

![CI](https://github.com/galax-io/gatling-picatinny/actions/workflows/ci.yml/badge.svg?branch=main) [![Maven Central](https://img.shields.io/maven-central/v/org.galaxio/gatling-picatinny_2.13.svg?color=success)](https://search.maven.org/search?q=org.galaxio.gatling-picatinny) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
## Table of contents

* [General info](#general-info)
* [Installation](#installation)
* [Usage](#usage)
    * [config](#config)
    * [startup banner and diagnostics](#startup-banner-and-diagnostics)
    * [feeders](#feeders)
        * [Faker API](#faker-api)
        * [Legacy feeders](#legacy-feeders)
        * [HC Vault feeder](#hc-vault-feeder)
        * [SeparatedValuesFeeder](#separatedvaluesfeeder)
        * [Phone Feeders](#phone-feeders)
    * [profile](#profile)
    * [redis](#redis)
    * [templates](#templates)
    * [utils](#utils)
    * [assertion](#assertion)
    * [transactions](#transactions)
    * [example](#example)
* [Built with](#built-with)
* [Help](#help)
* [License](#license)
* [Authors](#authors)
* [Acknowledgments](#acknowledgments)

## General info

A Scala toolkit that extends the Gatling DSL with production‑ready utilities (feeders, transactions, assertions, templates, config helpers, and integrations like Redis) to build faster, more reliable performance tests.

## Installation

### Using Gatling Template Project

If you are using galax-io/gatling-template.g8, you already have all dependencies in
it. [Gatling Template Project](https://github.com/galax-io/gatling-template.g8.git)

### Install manually

Add the dependency and pick the latest version (see the Maven Central badge at the top of this README):

```scala
libraryDependencies += "org.galaxio" %% "gatling-picatinny" % "<latest>"
```

## Usage

### config

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

```text
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

```
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

```text
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

Config values are logged when they are read. Paths containing words like `password`, `secret`, `token`, `apiKey`, or
`credential` are masked in logs as `******`.

### startup banner and diagnostics

Picatinny provides explicit utility methods for startup output. Put them where it makes sense for your project: in a
simulation class, a shared package object, or another project bootstrap point.

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

### feeders

Picatinny provides two feeder APIs: the **Faker API** (composable, domain-oriented generators) and the **legacy `Random*Feeder` helpers** (simple one-liner feeders). Both produce standard Gatling feeders. See [docs/faker-api.md](docs/faker-api.md) for the full Faker API reference.

#### Faker API

Lazy `Generator[A]` values that compose with `map`/`flatMap` and plug into Gatling via `GeneratedFeeder`.

Scala:

```scala
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.feeders.faker._

val users = GeneratedFeeder(
  "email"  -> Faker.internet.email(),
  "phone"  -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
  "inn"    -> Faker.ru.inn.person(),
  "amount" -> Faker.finance.amount(BigDecimal(100), BigDecimal(5000)),
)
```

Java:

```java
import static org.galaxio.gatling.javaapi.FakerApi.*;
import static org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder;

var users = GeneratedFeeder(
    field("email", email()),
    field("phone", phoneMobile(countryRU(), phoneFormatE164())),
    field("inn", innPerson()),
    field("amount", amount(100, 5000))
);
```

Kotlin:

```kotlin
import org.galaxio.gatling.javaapi.FakerApi.*
import org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder

val users = GeneratedFeeder(
    field("email", email()),
    field("phone", phoneMobile(countryRU(), phoneFormatE164())),
    field("inn", innPerson()),
    field("amount", amount(100.0, 5000.0)),
)
```

Enrich existing Gatling feeders (Scala):

```scala
val enriched = csv("users.csv").circular
  .withGenerated("traceId", Faker.uuid.string)
  .withGenerated("sessionPhone", Faker.phone.mobile(Country.AR))
```

#### Legacy feeders

> **Deprecated** — prefer the Faker API for new projects. Legacy feeders remain fully supported.

Scala:

```scala
import org.galaxio.gatling.feeders._

val uuidFeeder   = RandomUUIDFeeder("uuid")
val phoneFeeder  = RandomPhoneFeeder("phone")
val stringFeeder = RandomStringFeeder("randomString", 10)
val panFeeder    = RandomPANFeeder("pan", "421345")
val innFeeder    = RandomNatITNFeeder("inn")
```

Java:

```java
import static org.galaxio.gatling.javaapi.Feeders.*;

var uuidFeeder   = RandomUUIDFeeder("uuid");
var phoneFeeder  = RandomPhoneFeeder("phone");
var stringFeeder = RandomStringFeeder("randomString", 10);
var panFeeder    = RandomPANFeeder("pan", "421345");
var innFeeder    = RandomNatITNFeeder("inn");
```

Kotlin:

```kotlin
import org.galaxio.gatling.javaapi.Feeders.*

val uuidFeeder   = RandomUUIDFeeder("uuid")
val phoneFeeder  = RandomPhoneFeeder("phone")
val stringFeeder = RandomStringFeeder("randomString", 10)
val panFeeder    = RandomPANFeeder("pan", "421345")
val innFeeder    = RandomNatITNFeeder("inn")
```

#### HC Vault feeder

Creates feeder capable of retrieving secret data from HC Vault

- authorises via approle;
- uses v1 API;
- works with kv Secret Engine;
- does not iterate over keys, returns full map with keys it found on each call;
- params:
    - vaultUrl - vault URL *e.g. "https://vault.ru"*
    - secretPath - path to secret data within your vault *e.g. "testing/data"*
    - roleId - approle login
    - secretId - approle password
    - keys - list of keys you are willing to retrieve from vault

Scala example:
```scala
  val vaultFeeder = VaultFeeder(vaultUrl, secretPath, roleId, secretId, keys)
```

Java example:
```Java
  Iterator<Map<String, Object>> vaultFeeder = VaultFeeder(vaultUrl, secretPath, roleId, secretId, keys);
```

Kotlin example:
```Kotlin
  val vaultFeeder = VaultFeeder(vaultUrl, secretPath, roleId, secretId, keys)
```

Additional one-record data source helpers:

- `VaultFeeder.withToken(vaultUrl, secretPath, vaultToken, keys)` reads Vault data when CI already provides a token.
- `VaultFeeder.fromPaths(vaultUrl, roleId, secretId, paths)` merges selected keys from several Vault paths.
- `EnvFeeder(keys, prefix)` reads selected environment variables into a feeder record.
- `HttpJsonFeeder(url, keys, headers)` reads selected top-level string fields from a JSON HTTP endpoint.

#### SeparatedValuesFeeder

Creates a feeder with separated values from a source String, Seq[String] or Seq[Map[String, Any]].

- params:
    - paramName - feeder name
    - source - data source
    - separator - ",", ";", "\t" or other delimiter which separates values. You can also use following methods for the
      most common separators: .csv(...), .ssv(...), .tsv(...)

Get separated values from a source: String

Scala example:
```scala
val sourceString = "v21;v22;v23"
val separatedValuesFeeder: FeederBuilderBase[String] =
  SeparatedValuesFeeder("someValues", sourceString, ';') // Vector(Map(someValues -> v21), Map(someValues -> v22), Map(someValues -> v23))
```

Java example:
```Java
String sourceString = "v21;v22;v23";
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.apply("someValues", sourceString, ';');
```

Kotlin example:
```Kotlin
val sourceString = "v21;v22;v23"
val separatedValuesFeeder = SeparatedValuesFeeder.apply("someValues", sourceString, ';')
```

Get separated values from a source: Seq[String]

Scala example:
```scala
val sourceSeq = Seq("1,two", "3,4")
val separatedValuesFeeder: FeederBuilderBase[String] =
  SeparatedValuesFeeder.csv("someValues", sourceSeq) // Vector(Map(someValues -> 1), Map(someValues -> two), Map(someValues -> 3), Map(someValues -> 4))
```

Java example:
```Java
List<Map<String, Object>> sourceList = Arrays.asList("1,two", "3,4");
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.csv("someValues", sourceList);
```

Kotlin example:
```Kotlin
var sourceList = listOf("1,two", "3,4")
var separatedValuesFeeder1 = SeparatedValuesFeeder.csv("someValues", sourceList)
```

Get separated values from a source: Seq[Map[String, Any]]

Scala example:
```scala
val vaultFeeder: FeederBuilderBase[String] = Vector(
  Map(
    "HOSTS" -> "host11,host12",
    "USERS" -> "user11",
  ),
  Map(
    "HOSTS" -> "host21,host22",
    "USERS" -> "user21,user22,user23",
  ),
)
val separatedValuesFeeder: FeederBuilderBase[String] =
  SeparatedValuesFeeder(None, vaultFeeder.readRecords, ',') // Vector(Map(HOSTS -> host11), Map(HOSTS -> host12), Map(USERS -> user11), Map(HOSTS -> host21), Map(HOSTS -> host22), Map(USERS -> user21), Map(USERS -> user22), Map(USERS -> user23))
```

Java example:
```Java
List<Map<String, Object>> vaultData = Arrays.asList(Map.of("HOSTS","host11,host12"), Map.of("USERS", "user21,user22,user23"));
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.apply(Optional.empty(), vaultData, ',');
```

Kotlin example:
```Kotlin
var sourceList = listOf(Map.of("HOSTS", "host11,host12"), Map.of("USERS", "user21,user22,user23"))
var separatedValuesFeeder1 = SeparatedValuesFeeder.csv(null, sourceList)
```

#### Phone Feeders

Creates a feeder with phone numbers with formats from json file or `case class PhoneFormat`

Simple phone feeder

Scala example:
```scala
val simplePhoneNumber: Feeder[String] = RandomPhoneFeeder("simplePhoneFeeder")
```

Java example:
```Java
Iterator<Map<String, Object>> simplePhoneNumber = RandomPhoneFeeder("simplePhoneFeeder");
```

Kotlin example:
```Kotlin
val simplePhoneNumber = RandomPhoneFeeder("simplePhoneNumber")
```

Phone feeder with custom formats

Scala example:
```scala
 val ruMobileFormat: PhoneFormat = PhoneFormat(
  countryCode = "+7",
  length = 10,
  areaCodes = Seq("903", "906", "908"),
  prefixes = Seq("55", "81", "111"),
  format = "+X XXX XXX-XX-XX")

  val randomPhoneNumber: Feeder[String]                 =
  RandomPhoneFeeder("randomPhoneNumber", ruMobileFormat)
```

Java example:
```Java
PhoneFormat ruMobileFormat = PhoneFormatBuilder.apply("+7", 10, Arrays.asList("945", "946"), "+X XXX XXX-XX-XX", Arrays.asList("55", "81", "111"));
Iterator<Map<String, Object>> randomPhoneNumber = RandomPhoneFeeder("randomPhoneNumber", ruMobileFormat);
```

Kotlin example:
```Kotlin
val ruMobileFormat = PhoneFormatBuilder.apply(
        "+7",
        10,
        listOf("945", "946"),
        "+X XXX XXX-XX-XX",
        listOf("55", "81", "111")
    )
val randomPhoneNumber = RandomPhoneFeeder("randomPhoneNumber", ruMobileFormat)
```

Phone feeder with custom formats with file

Creates file with formats, for example RESOURCES/phoneTemplates/ru.json

```json
{
  "formats": [
    {
      "countryCode": "+7",
      "length": 10,
      "areaCodes": ["903", "906"],
      "prefixes": ["123", "321", "132", "231"],
      "format": "+X(XXX)XXXXXXX"
    },
    {
      "countryCode": "8",
      "length": 10,
      "areaCodes": ["495", "499"],
      "prefixes": ["81", "82", "83"],
      "format": "X(XXX)XXX-XX-XX"
    }
  ]
}
```

Scala example:
```scala
val phoneFormatsFromFile: String   = "phoneTemplates/ru.json"
val randomE164PhoneNumberFromJson: Feeder[String]     =
    RandomPhoneFeeder("randomE164PhoneNumberFile", phoneFormatsFromFile, TypePhone.E164PhoneNumber)
```

Java example:
```Java
String phoneFormatsFromFile = "phoneTemplates/ru.json";
Iterator<Map<String, Object>> randomE164PhoneNumberFromJson = RandomPhoneFeeder("randomE164PhoneNumberFile", phoneFormatsFromFile, TypePhone.E164PhoneNumber());
```

Kotlin example:
```Kotlin
val phoneFormatsFromFile = "phoneTemplates/ru.json"
val randomE164PhoneNumberFromJson = RandomPhoneFeeder("randomE164PhoneNumberFile", phoneFormatsFromFile, TypePhone.E164PhoneNumber())
```

### profile

#### Features:

* Load profile configs from HOCON or YAML files
* Common traits to create profiles for any protocol
* HTTP profile as an example

#### Import:

```scala
import org.galaxio.gatling.profile._
import pureconfig.generic.auto._
```

#### Using:

HOCON configuration example:

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

YAML configuration example:

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

*Simulation setUp*

```scala
  class test extends Simulation {
  val profileConfigName = "profile.conf"
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

#### New style profile:

New profile YAML configuration example:

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

Optional fields: groups, headers, body.

If there are no required fields, an exception will be thrown for the missing field.

*Simulation setUp*

Scala example:
```scala
class Debug extends Simulation {
  val pathToProfile = "path/to/profile.yml"
  val scn = ProfileBuilderNew.buildFromYaml(pathToProfile).selectProfile("maxPerf").toRandomScenario

  setUp(
    scn.inject(
      atOnceUsers(10)
    ).protocols(httpProtocol)
  )
          .maxDuration(10)
}
```

Java example:

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

Kotlin example:
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

### redis

This module allows you to use Redis commands as Gatling scenario actions.

#### Features:

- Support for 27 Redis commands across 6 data types (Strings, Hashes, Lists, Sets, Keys, Counters)
- Save command results to Gatling session with `.saveAs("variable")`
- Custom request names for statistics with `.requestName("name")`
- Support Gatling EL expressions in keys and values

#### Read before use:

- Methods are not taken into account in Gatling statistics by default. Use `.requestName("name")` to track them.
- Not intended for load testing of Redis.

#### Import:

Scala:

```scala
import com.redis.RedisClientPool
import org.galaxio.gatling.redis.RedisActionBuilder._
```

Java:

```java
import io.gatling.javaapi.redis.RedisClientPool;
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava;
```

Kotlin:

```kotlin
import io.gatling.javaapi.redis.RedisClientPool
import org.galaxio.gatling.javaapi.redis.RedisClientPoolJava
```

#### Using:

First you need to prepare RedisClientPool:

Scala:

```scala
val redisPool = new RedisClientPool("localhost", 6379)
```

Java:

```java
RedisClientPoolJava redisPool = new RedisClientPoolJava("localhost", 6379);
```

Kotlin:

```kotlin
val redisPool = RedisClientPoolJava("localhost", 6379)
```

Add Redis commands to your scenario chain:

Scala:

```scala
.exec(redisPool.SET("key", "value"))
.exec(redisPool.GET("key").saveAs("result"))
.exec(redisPool.DEL("key"))
```

Java:

```java
.exec(redisPool.SET("key", "value"))
.exec(redisPool.GET("key").saveAs("result"))
.exec(redisPool.DEL("key"))
```

Kotlin:

```kotlin
.exec(redisPool.SET("key", "value"))
.exec(redisPool.GET("key").saveAs("result"))
.exec(redisPool.DEL("key"))
```

#### Available commands:

| Category | Commands |
|----------|----------|
| Strings  | `GET`, `SET`, `GETSET`, `SETNX`, `SETEX`, `MGET`, `MSET`* |
| Counters | `INCR`, `INCRBY`, `DECR`, `DECRBY` |
| Hashes   | `HGET`, `HSET`, `HDEL`, `HGETALL`, `HMSET`*, `HMGET` |
| Lists    | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`, `LLEN` |
| Sets     | `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`, `SCARD` |
| Keys     | `EXISTS`, `EXPIRE`, `TTL`, `KEYS`, `DEL` |

\* `MSET` and `HMSET` are available only in the Scala API.

### templates

This module contains some syntax extensions for http requests with json body. It allows embed json-body in request
with `jsonBody` method for `HttpRequestBuilder`. And this module is provided ability to send request body templates from
files in resource subfolder `resources/templates` by filename. Sending of templates may be done with
method `postTemplate` from trait `Templates`

#### jsonBody

This part contains http request Json body DSL.

For use this you need import this:

```scala
import org.galaxio.gatling.templates.HttpBodyExt._
import org.galaxio.gatling.templates.Syntax._
```

Then use described later constructions for embed jsonBody in http requests. For example, you write something like this:

```scala
class SampleScenario {
  val sendJson: ScenarioBuilder =
    scenario("Post some")
      .exec(
        http("PostData")
          .post(url)
          .jsonBody(
            "id" - 23, // in json - "id" : 23 
            "name", // in json it interpreted as - "name" : get value from session variable #{name}
            "project" - ( // in json - "project" : { ... }
              "id" ~ "projectId", // in json - "id" : value from session var #{projectId}
              "name" - "Super Project", // in json - "name": "Super Project"
              "sub" > (1, 2, 3, 4, 5, 6) // in json - "sub" : [ 1,2,3,4,5,6 ]
            )
          )
      )
}
```

As result this scenario sent POST request with body:

```json
{
  "id": 23,
  "name": "Test",
  "project": {
    "id": 23421,
    "name": "Super Project",
    "sub": [
      1,
      2,
      3,
      4,
      5,
      6
    ]
  }
}
```

As you can see in the example:

- construction `"some_name" - <val>` map to `"some_name": <val>` in json;
- construction `"varName"` map to `"varName" : <get value from session variable #{varName}>` in json;
- construction `"some_name" ~ "sesVar"` map to `"some_name" : <value from session var #{sesVar}>` in json;
- `"some_name" > (<...items>)` map to array field `"some_name": [ ...items ]` in json;
- `"some_name" - (<...fields>)` map to object field `"some_name": { ...fields }` in json;

#### postTemplate

Suppose in folder resources/templates contains this:

```shell
$ tree resources/
.
├── gatling.conf
├── logback.xml
├── pools
│   └── example_pool.csv
├── simulation.conf
└── templates
    └── example_template1.json
    └── example_template2.json
```

For use templates in `resources/templates` you need import trait `Templates`.

```scala
import org.galaxio.gatling.templates.Templates._
```

Then add this trait to your Scenario and use `postTemplate` method like show later:

```scala
class SampleScenario extends Templates {
  val sendTemplates: ScenarioBuilder =
    scenario("Templates scenario")
      .exec(postTemplate("example_template1", "/post_route"))
      .exec(postTemplate("example_template2", "/post_route"))
}
```

This Scenario will send 2 post requests one with body from `example_template1.json`, second with body
from `example_template2.json` to route `$baseUrl/post_route`. In template files you may use
[gatling expression syntax](https://gatling.io/docs/gatling/reference/current/session/expression_el/).

### utils

#### jwt

#### Features:

* Generate JWT tokens and store them in Gatling sessions for signing requests
* HMAC (HS256/384/512) and RSA/EC (RS256, ES256, etc.) algorithms
* Standard claims DSL (iss, sub, aud, exp, iat, nbf) with automatic time-based claims
* Gatling EL expression support (`#{varName}`) for dynamic per-user claims
* Claim merging — combine base payload from resource with dynamic claims
* Bearer token helper (`setJwtAsBearer`)
* PEM key loading utilities

#### Import:

Scala:
```scala
import org.galaxio.gatling.utils.jwt._
```

Java:
```java
import org.galaxio.gatling.javaapi.utils.Jwt;
import org.galaxio.gatling.javaapi.utils.JwtKeysJ;
import org.galaxio.gatling.utils.jwt.JwtGeneratorBuilder;
import org.galaxio.gatling.utils.jwt.ClaimsBuilder;
```

Kotlin:
```kotlin
import org.galaxio.gatling.javaapi.utils.Jwt.*
import org.galaxio.gatling.javaapi.utils.JwtKeysJ
```

#### Basic usage (payload from template):

Scala:
```scala
val jwtGenerator = jwt("HS256", jwtSecretToken)
  .defaultHeader
  .payloadFromResource("jwtTemplates/payload.json")
```

Java:
```java
JwtGeneratorBuilder jwtGenerator = Jwt.jwt("HS256", "jwtSecretToken")
        .defaultHeader()
        .payloadFromResource("jwtTemplates/payload.json");
```

Kotlin:
```kotlin
val jwtGenerator = jwt("HS256", jwtSecretToken)
    .defaultHeader()
    .payloadFromResource("jwtTemplates/payload.json")
```

Payload templates support [Gatling EL](https://gatling.io/docs/gatling/reference/current/session/expression_el/) expressions:

```json
{
  "userName": "#{randomString}",
  "date": "#{simpleDate}",
  "phone": "#{randomPhone}"
}
```

#### Standard claims with ClaimsBuilder:

Scala:
```scala
val jwtGenerator = jwt("HS256", secret).defaultHeader
  .claims(ClaimsBuilder()
    .issuer("my-service")
    .subject("#{userId}")
    .audience("https://api.example.com")
    .expiresIn(5.minutes)
    .issuedAtNow
    .notBeforeNow
    .claim("role", "admin")
    .claimFromSession("tenantId", "#{tenantId}"))
```

Java:
```java
JwtGeneratorBuilder jwtGenerator = Jwt.jwt("HS256", secret).defaultHeader()
    .claims(Jwt.claims()
        .issuer("my-service")
        .subject("#{userId}")
        .audience("https://api.example.com")
        .expiresIn(Duration.ofMinutes(5))
        .issuedAtNow()
        .notBeforeNow()
        .claim("role", "admin")
        .claimFromSession("tenantId", "#{tenantId}"));
```

Kotlin:
```kotlin
val jwtGenerator = jwt("HS256", secret).defaultHeader()
    .claims(claims()
        .issuer("my-service")
        .subject("#{userId}")
        .audience("https://api.example.com")
        .expiresIn(Duration.ofMinutes(5))
        .issuedAtNow()
        .notBeforeNow()
        .claim("role", "admin")
        .claimFromSession("tenantId", "#{tenantId}"))
```

#### Claim merging:

You can combine a base payload from a resource file with dynamic claims.
ClaimsBuilder fields take precedence on conflict:

```scala
val gen = jwt("HS256", secret).defaultHeader
  .payloadFromResource("jwtTemplates/baseClaims.json")
  .claims(ClaimsBuilder().subject("#{userId}").expiresIn(5.minutes))
```

#### RSA/EC signing:

Scala:
```scala
val privateKey = JwtKeys.rsaPrivateKeyFromResource("keys/private.pem")
val jwtGenerator = jwt("RS256", privateKey).defaultHeader
  .claims(ClaimsBuilder().issuer("auth-service").expiresIn(1.hour))
```

Java:
```java
PrivateKey privateKey = JwtKeysJ.rsaPrivateKeyFromResource("keys/private.pem");
JwtGeneratorBuilder jwtGenerator = Jwt.jwt("RS256", privateKey).defaultHeader()
    .claims(Jwt.claims().issuer("auth-service").expiresIn(Duration.ofHours(1)));
```

Kotlin:
```kotlin
val privateKey = JwtKeysJ.rsaPrivateKeyFromResource("keys/private.pem")
val jwtGenerator = jwt("RS256", privateKey).defaultHeader()
    .claims(claims().issuer("auth-service").expiresIn(Duration.ofHours(1)))
```

Available key loading methods:
- `rsaPrivateKeyFromResource` / `rsaPrivateKeyFromFile`
- `ecPrivateKeyFromResource` / `ecPrivateKeyFromFile`
- `rsaPublicKeyFromResource` / `rsaPublicKeyFromFile` (for verification)
- `ecPublicKeyFromResource` / `ecPublicKeyFromFile` (for verification)

#### Header/payload DSL:

```scala
jwt("HS256", secret)
  .header("""{"alg": "HS256","typ": "JWT", "customField": "customData"}""")
  .headerFromResource("jwtTemplates/header.json")
  .defaultHeader
  .payload("""{"sub": "#{userId}","scope": "api"}""")
  .payloadFromResource("jwtTemplates/payload.json")
```

#### Signing requests:

Scala:
```scala
.exec(_.setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain(jwtCookieDomain).withPath("/")))
```

Java:
```java
.exec(Jwt.setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain("jwtCookieDomain").withPath("/")))
```

Kotlin:
```kotlin
.exec(setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain("jwtCookieDomain").withPath("/")))
```

#### Bearer token (Authorization header):

Scala:
```scala
.exec(_.setJwtAsBearer(jwtGenerator))
.exec(http("request").get("/api").header("Authorization", "#{Authorization}"))
```

Java:
```java
.exec(Jwt.setJwtAsBearer(jwtGenerator))
.exec(http("request").get("/api").header("Authorization", "#{Authorization}"))
```

Kotlin:
```kotlin
.exec(setJwtAsBearer(jwtGenerator))
.exec(http("request").get("/api").header("Authorization", "#{Authorization}"))
```

You can also specify a custom session key: `setJwtAsBearer(jwtGenerator, "X-Auth")`

### assertion

Module helps to load assertion configs from YAML files

#### Import:

```scala
import org.galaxio.gatling.assertions.AssertionsBuilder.assertionFromYaml
```

#### Using:

File nfr contains non-functional requirements.

Requirements supported by Picatinny:

|  requirement|  key |
|---|---|
|  99th percentile of the responseTime | 99 перцентиль времени выполнения  |
|  95th percentile of the responseTime | 95 перцентиль времени выполнения  |
|  75th percentile of the responseTime |  75 перцентиль времени выполнения |
|  50th percentile of the responseTime |  50 перцентиль времени выполнения |
|  percent of the failedRequests |  Процент ошибок |
|  maximum of the responseTime |  Максимальное время выполнения |

YAML configuration example:

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

*Scala example*

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

*Java example*

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

*Kotlin example*

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

### transactions

This extension introduce new syntax (`startTransaction`/`endTransaction`) for gatling scenario. Transaction is union of
actions, that able to measure summary response time of actions with pauses. It is same as groups, but response time
measuring include pauses, and you may pass endTime manually. That make possible write something like:

```scala
exec(Actions.createEntity())
  .startTransaction("transaction1")
  .doWhile(_ ("i").as[Int] < 10)(
    feed(feeder)
      .exec(Actions.insertTest())
      .pause(2)
      .exec(Actions.selectTest)
  )
  .endTransaction("transaction1")
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)

```

Java example:

```java
exec(Actions.createEntity())
  .exec(startTransaction("transaction1"))
  .exec(Actions.insertTest())
  .pause(2)
  .exec(Actions.selectTest)
  .exec(endTransaction("transaction1"))
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)
```

Kotlin example:

```kotlin
exec(Actions.createEntity())
  .exec(startTransaction("transaction1"))
  .exec(Actions.insertTest())
  .pause(2)
  .exec(Actions.selectTest)
  .exec(endTransaction("transaction1"))
  .exec(Actions.batchTest)
  .exec(Actions.selectAfterBatch)
```

#### Usage:

To use this, you need Gatling version >= **3.11.5** and import this in your Scenarios and Simulations:

```scala
import org.galaxio.gatling.transactions.Predef._
```

**Attention!**
*Your simulation should inherit the class `SimulationWithTransactions` instead of `Simulation`, then the transaction
mechanism will work correctly.*

#### Example Simulation:

```scala
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
..

.
import org.galaxio.gatling.transactions.Predef._

object DebugScenario {
  val scn: ScenarioBuilder = scenario("Debug")
    .exec(Actions.createEntity())
    .startTransaction("transaction1")
    .doWhile(_ ("i").as[Int] < 10)(
      feed(feeder)
        .exec(Actions.insertTest())
        .pause(2)
        .exec(Actions.selectTest)
    )
    .endTransaction("transaction1")
    .exec(Actions.batchTest)
    .exec(Actions.selectAfterBatch)
}

class DebugTest extends SimulationWithTransactions {

  setUp(
    DebugScenario.scn.inject(atOnceUsers(1))
  ).protocols(dataBase)

}
```

### examples

Standalone Gatling projects in the `examples/` directory:

| Project | Language | Build tool | Feeder API |
|---------|----------|------------|------------|
| `examples/scala-sbt-example` | Scala | sbt | Faker API + legacy feeders |
| `examples/java-maven-example` | Java | Maven | Faker API (`FakerApi`) + legacy feeders |
| `examples/kotlin-gradle-example` | Kotlin | Gradle | Faker API (`FakerApi`) + legacy feeders |

For a new Scala sbt project, prefer the Galaxio Gatling template:

```bash
galaxio template init gatling/scala-sbt
```

```scala
libraryDependencies += "org.galaxio" %% "gatling-picatinny" % "<latest>"
```

## Testing

To test your changes use `sbt test`.

## Built with

* Scala version: 2.13.16
* SBT version: 1.11.4
* Gatling version: 3.11.5
* SBT Gatling plugin version: 4.13.3
* SBT CI release plugin version: 1.11.1
* json4s version: 4.1.0-M8
* pureconfig version: 0.17.9
* scalatest version: 3.2.19
* scalacheck version: 1.18.1
* scalamock version: 5.2.0
* generex version: 1.0.2
* jwt-core version: 11.0.2

## Help

telegram: @qa_load

Gatling docs: https://gatling.io/docs/gatling/reference/current/general/

## Versioning

We use [SemVer](https://semver.org/) for versioning. For the versions available, see
the [tags on this repository](https://github.com/galax-io/gatling-picatinny/tags).

## Authors

* **Maksim Sitnikov** - *profile module* 

* **Chepkasov Sergey** - *feeders, config, utils modules* 

* **Kalyokin Vyacheslav** - *templates module* 

* **Akhaltsev Ioann** - *founder and spiritual guidance*

See also the list of [contributors](https://github.com/galax-io/gatling-picatinny/graphs/contributors) who
participated in this project.

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details

## Acknowledgments

TBD
