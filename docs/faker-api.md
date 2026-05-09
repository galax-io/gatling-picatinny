# Picatinny Faker API

The faker API is a new user-first layer for generating test data in Gatling simulations.
It is available as a new API while the old `Random*Feeder` helpers remain compatible and deprecated.

```scala
import org.galaxio.gatling.feeders.faker.Predef._
import org.galaxio.gatling.feeders.faker._
```

## Core Idea

`Faker.*` methods return `Generator[A]`, not an eager value.

```scala
val email: Generator[String] = Faker.internet.email()
val sampled: String = email.sample()
```

This lets you reuse the same generator directly, compose it with `map`/`flatMap`, or pass it into a Gatling feeder.

## Generated Gatling Feeder

Use `GeneratedFeeder` when a scenario needs generated session attributes.

```scala
val users =
  GeneratedFeeder(
    "email" -> Faker.internet.email(),
    "phone" -> Faker.phone.mobile(Country.RU, PhoneFormatMode.E164),
    "inn" -> Faker.ru.inn.person(),
    "amount" -> Faker.finance.amount(BigDecimal(100), BigDecimal(5000))
  )
```

Gatling usage:

```scala
val scn = scenario("checkout")
  .feed(users)
  .exec(
    http("checkout")
      .post("/checkout")
      .body(StringBody("""{"email":"#{email}","phone":"#{phone}","amount":"#{amount}"}"""))
  )
```

Mixed records are represented as `Feeder[Any]`, because Gatling session maps commonly contain strings, numbers, booleans, and dates together. Single-field feeders keep their value type:

```scala
val emails = Faker.internet.email().toFeeder("email") // Feeder[String]
```

## Dependent Fields

Use a generator `for` comprehension when one field depends on another.

```scala
val users =
  GeneratedFeeder.records {
    for {
      gender <- Faker.person.gender()
      name <- Faker.person.fullName(gender)
      email <- Faker.internet.email(name, "example.com")
    } yield Map(
      "gender" -> gender.value,
      "name" -> name,
      "email" -> email
    )
  }
```

## Existing Gatling Feeders

Picatinny should complement Gatling's built-in feeders, not duplicate them. Keep using Gatling `csv`, `jsonFile`, `jdbcFeeder`, and other native sources. Use faker syntax only to enrich or transform records.

```scala
val enriched =
  csv("users.csv").circular
    .withGenerated("traceId", Faker.uuid.string)
    .withGenerated("sessionPhone", Faker.phone.mobile(Country.AR))
```

## Collections

Use collection syntax when data is already in memory.

```scala
val countries =
  List("RU", "AR", "BR")
    .toFeeder("country")
    .circular
```

Map domain objects explicitly to Gatling records:

```scala
case class User(id: String, email: String)

val users =
  List(User("1", "a@example.com"))
    .toFeeder(user => Map("id" -> user.id, "email" -> user.email))
    .queue
```

The returned collection is a normal Gatling-compatible in-memory feeder source, so use Gatling's own `.queue`, `.circular`, `.random`, and `.shuffle` strategies.

## Generator Catalog

The first slice includes:

- `Faker.uuid.string`
- `Faker.number.int`, `long`, `double`, `float`, `boolean`
- `Faker.string.alphabetic`, `alphanumeric`, `numeric`, `hex`, `cyrillic`, `fromAlphabet`, `lengthBetween`
- `Faker.person.gender`, `firstName`, `lastName`, `prefix`, `fullName`, `jobTitle`
- `Faker.internet.email`, `username`, `domain`, `url`, `password`, `userAgent`, `ipv4`, `ipv6`
- `Faker.location.country`, `countryCode`, `city`, `streetName`, `streetAddress`, `postalCode`, `latitude`, `longitude`
- `Faker.localization.currency`, `languageCode`
- `Faker.date.today`, `now`, `between`, `past`, `future`, `offset`, `range`
- `Faker.finance.pan`, `amount`, `money`, `currency`, `accountNumber`, `bic`, `iban`, `transactionId`
- `Faker.commerce.productName`, `category`, `sku`, `orderId`, `price`
- `Faker.phone.mobile`, `tollFree`, `fromFormats`
- `Faker.passport.ru`, `Faker.passport.number`
- `Faker.ru.inn.person`, `Faker.ru.inn.company`, `kpp`, `ogrn`, `ogrnip`, `snils`
- `Faker.br.cpf`
- `Faker.ar.dni`
- `Faker.weather.condition`, `temperatureCelsius`, `humidityPercent`, `pressureHPa`
- `Faker.lorem.word`, `words`, `sentence`

## Predefined Data

`FakerData` exposes the predefined catalogs used by `Faker`, so scenarios can build custom generators without copying internal data.

```scala
val qaJob = Faker.oneOf(FakerData.jobTitles.filter(_.contains("Engineer")))
val ruCity = Faker.oneOf(FakerData.citiesByCountry(Country.RU))
```

Available catalogs include:

- `FakerData.maleFirstNames`, `femaleFirstNames`, `lastNames`, `personPrefixes`, `jobTitles`
- `FakerData.domains`, `userAgents`
- `FakerData.citiesByCountry`, `streetNames`
- `FakerData.currencies`
- `FakerData.products`, `categories`
- `FakerData.weatherConditions`
- `FakerData.loremWords`
- `FakerData.phoneFormatsByCountry`

## Migration Examples

Old:

```scala
RandomStringFeeder("name", 10)
```

New:

```scala
GeneratedFeeder.single("name", Faker.string.alphanumeric(10))
```

Old:

```scala
RandomNatITNFeeder("inn")
```

New:

```scala
GeneratedFeeder.single("inn", Faker.ru.inn.person())
```

Old:

```scala
RandomDateFeeder("createdAt", 30, 0)
```

New:

```scala
GeneratedFeeder.single(
  "createdAt",
  Faker.date.past(days = 30).format("yyyy-MM-dd")
)
```
