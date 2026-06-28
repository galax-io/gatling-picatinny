# Feeders

[← Back to README](../README.md)

> The Faker API has a dedicated reference: [faker-api.md](faker-api.md).

## Overview

Picatinny provides two feeder APIs: the **Faker API** (composable, domain-oriented generators) and the **legacy `Random*Feeder` helpers** (simple one-liner feeders). Both produce standard Gatling feeders. See [docs/faker-api.md](faker-api.md) for the full Faker API reference.

## Faker API

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

## Legacy feeders

> **Deprecated — migrate to the [Faker API](faker-api.md).** All legacy feeders (the `Random*Feeder` family, `RegexFeeder`) and the legacy `RandomDataGenerators` helpers are superseded by the composable `Faker.*` + `GeneratedFeeder`. They still work for now; see [migration.md](migration.md#deprecations).

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

## HC Vault feeder

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

```java
  Iterator<Map<String, Object>> vaultFeeder = VaultFeeder(vaultUrl, secretPath, roleId, secretId, keys);
```

Kotlin example:

```kotlin
  val vaultFeeder = VaultFeeder(vaultUrl, secretPath, roleId, secretId, keys)
```

Additional one-record data source helpers:

- `VaultFeeder.withToken(vaultUrl, secretPath, vaultToken, keys)` reads Vault data when CI already provides a token.
- `VaultFeeder.fromPaths(vaultUrl, roleId, secretId, paths)` merges selected keys from several Vault paths.
- `EnvFeeder(keys, prefix)` reads selected environment variables into a feeder record.
- `HttpJsonFeeder(url, keys, headers)` reads selected top-level string fields from a JSON HTTP endpoint.

## SeparatedValuesFeeder

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

```java
String sourceString = "v21;v22;v23";
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.apply("someValues", sourceString, ';');
```

Kotlin example:

```kotlin
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

```java
List<Map<String, Object>> sourceList = Arrays.asList("1,two", "3,4");
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.csv("someValues", sourceList);
```

Kotlin example:

```kotlin
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

```java
List<Map<String, Object>> vaultData = Arrays.asList(Map.of("HOSTS","host11,host12"), Map.of("USERS", "user21,user22,user23"));
Iterator<Map<String, Object>> separatedValuesFeeder = SeparatedValuesFeeder.apply(Optional.empty(), vaultData, ',');
```

Kotlin example:

```kotlin
var sourceList = listOf(Map.of("HOSTS", "host11,host12"), Map.of("USERS", "user21,user22,user23"))
var separatedValuesFeeder1 = SeparatedValuesFeeder.apply(Optional.empty(), sourceList, ',')
```

## Phone Feeders

Creates a feeder with phone numbers with formats from json file or `case class PhoneFormat`

Simple phone feeder

Scala example:

```scala
val simplePhoneNumber: Feeder[String] = RandomPhoneFeeder("simplePhoneFeeder")
```

Java example:

```java
Iterator<Map<String, Object>> simplePhoneNumber = RandomPhoneFeeder("simplePhoneFeeder");
```

Kotlin example:

```kotlin
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

```java
PhoneFormat ruMobileFormat = PhoneFormatBuilder.apply("+7", 10, Arrays.asList("945", "946"), "+X XXX XXX-XX-XX", Arrays.asList("55", "81", "111"));
Iterator<Map<String, Object>> randomPhoneNumber = RandomPhoneFeeder("randomPhoneNumber", ruMobileFormat);
```

Kotlin example:

```kotlin
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

```java
String phoneFormatsFromFile = "phoneTemplates/ru.json";
Iterator<Map<String, Object>> randomE164PhoneNumberFromJson = RandomPhoneFeeder("randomE164PhoneNumberFile", phoneFormatsFromFile, TypePhone.E164PhoneNumber());
```

Kotlin example:

```kotlin
val phoneFormatsFromFile = "phoneTemplates/ru.json"
val randomE164PhoneNumberFromJson = RandomPhoneFeeder("randomE164PhoneNumberFile", phoneFormatsFromFile, TypePhone.E164PhoneNumber())
```

