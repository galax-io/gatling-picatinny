# JWT

[← Back to README](../README.md)

Generate JWT tokens and store them in Gatling sessions for signing requests.

## Features

- HMAC (HS256/384/512) and RSA/EC (RS256, ES256, etc.) algorithms
- Standard claims DSL (`iss`, `sub`, `aud`, `exp`, `iat`, `nbf`) with automatic time-based claims
- Gatling EL expression support (`#{varName}`) for dynamic per-user claims
- Claim merging — combine base payload from resource with dynamic claims
- Bearer token helper (`setJwtAsBearer`)
- PEM key loading utilities

## Import

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

## Basic usage (payload from template)

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

## Standard claims with ClaimsBuilder

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

## Claim merging

Combine a base payload from a resource file with dynamic claims. `ClaimsBuilder` fields take precedence on conflict:

```scala
val gen = jwt("HS256", secret).defaultHeader
  .payloadFromResource("jwtTemplates/baseClaims.json")
  .claims(ClaimsBuilder().subject("#{userId}").expiresIn(5.minutes))
```

## RSA/EC signing

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
- `rsaPublicKeyFromResource` / `rsaPublicKeyFromFile`
- `ecPublicKeyFromResource` / `ecPublicKeyFromFile`

## Header/payload DSL

```scala
jwt("HS256", secret)
  .header("""{"alg": "HS256","typ": "JWT", "customField": "customData"}""")
  .headerFromResource("jwtTemplates/header.json")
  .defaultHeader
  .payload("""{"sub": "#{userId}","scope": "api"}""")
  .payloadFromResource("jwtTemplates/payload.json")
```

## Signing requests

Scala:

```scala
.exec(_.setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain(".example.com").withPath("/")))
```

Java:

```java
.exec(Jwt.setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain(".example.com").withPath("/")))
```

Kotlin:

```kotlin
.exec(setJwt(jwtGenerator, "jwtToken"))
.exec(addCookie(Cookie("JWT_TOKEN", "#{jwtToken}").withDomain(".example.com").withPath("/")))
```

## Bearer token (Authorization header)

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

Custom session key: `setJwtAsBearer(jwtGenerator, "X-Auth")`

## Performance

An RS256 signature with a 2048-bit key costs about 1 ms of CPU per token: 0.93 ms on an Apple M2 Pro, as the
[measurement below](#measured-cost-of-a-token) records. An HS256 token costs about 5 µs, so the signature is almost
all of what an RS256 token costs.

Generate the token once per virtual user, at the start of the scenario or right after login, and reuse it from the
session through `#{jwt}` or `#{Authorization}`. Do not call `setJwt` before every request: with RS256 that is a
millisecond of CPU per request. When the token must be renewed, generate a new one when it expires, not on every
request.

```scala
val scn = scenario("API")
  .exec(_.setJwtAsBearer(jwtGenerator)) // once per virtual user
  .repeat(100) {
    exec(http("request").get("/api").header("Authorization", "#{Authorization}"))
  }
```

### Measured cost of a token

The JMH benchmark [`JwtBenchmark`](../src/main/scala/org/galaxio/gatling/utils/jwt/JwtBenchmark.scala) recorded these
averages on 2026-11-20: Apple M2 Pro, macOS 27.2, OpenJDK 17.0.10, JMH 1.37, 3 warmup and 5 measurement iterations of
1 s, one thread. Each token has the default header and seven claims: `sub`, four more claims, `iat` and `exp`.

| Benchmark method | Token | Time per token |
|---|---|---:|
| `hs256TokenStaticClaims` | HS256, static claims | 4.6 ± 0.1 µs |
| `hs256TokenFiveElClaims` | HS256, five claims from the session by EL | 5.5 ± 0.8 µs |
| `rs256TokenFiveElClaims` | RS256, 2048-bit key, the same claims | 927.0 ± 28.0 µs |

Each method ran in its own `sbt Jmh/run`, with JMH inside the JVM that `Jmh/run` forks (`-f 0`):

```bash
sbt "Jmh/run -bm avgt -tu ns -f 0 -wi 3 -i 5 .*JwtBenchmark.rs256TokenFiveElClaims$"
```

To measure on your machine, run `sbt "Jmh/run .*JwtBenchmark.*"`.
