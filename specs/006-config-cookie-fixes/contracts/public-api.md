# Public API Contract: Config & Cookie Correctness Fixes (v1.22.0)

This feature changes **behavior**, not **signatures**. Every public surface below keeps its exact signature; the contract records what callers can rely on before and after.

## `org.galaxio.gatling.utils.IntensityConverter`

```scala
def getIntensityFromString(intensity: String): Double   // signature UNCHANGED
```

- **Before**: decimals beyond the first fractional digit silently truncated (`"0.25 rps"` → `0.2`); some malformed inputs silently mis-parsed.
- **After**: exact decimal parsing (`"0.25 rps"` → `0.25`); clearly-malformed input throws `IllegalArgumentException`. The message keeps the `"Simulation param for intensity incorrect"` prefix and now appends a specific cause (e.g. `: unsupported unit 'jpeg' (use rps, rpm or rph)`) — type unchanged, prefix unchanged, suffix added for diagnostics.
- **Compat**: bug fix. Previously-correct inputs (whole numbers, single-decimal, valid units) return identical values. Java facade `javaapi/utils/IntensityConverter.getIntensityFromString` delegates unchanged.
- **Behavior delta (release note)**: negative input such as `"-5"` / `"-5 rps"` now **throws** instead of silently extracting `5` (the old `findAllIn` matched a substring). `requirePositive` already rejected such values downstream, so no valid config is affected — but note it in the v1.22.0 release notes.

## `org.galaxio.gatling.storage.CookieParser`

```scala
def parse(rawSetCookie: String, defaultDomain: String): Seq[ParsedCookie]   // signature UNCHANGED
case class ParsedCookie(...)                                                 // shape UNCHANGED
```

- **Before**: a non-numeric `Max-Age` was dropped silently (`maxAge = None`, no log).
- **After**: identical return value (`maxAge = None`), plus a single WARN naming the offending value. Valid/absent `Max-Age` unchanged and silent.
- **Compat**: additive observability only; return shape identical.

## `org.galaxio.gatling.storage.SessionStorage`

```scala
def restoreCookies(setCookieField: String, domain: String): ChainBuilder   // signature UNCHANGED
```

- **Before**: copied each cookie's `name`→`value` into a session attribute only; cookies were **not** registered with the jar and therefore not auto-sent.
- **After**: still sets the session attribute (additive/compat) AND registers each cookie in the Gatling cookie jar via the public `addCookie` DSL, scoped to `domain` with default path `/`, so cookies auto-attach to subsequent requests to that domain. Per-cookie `path`/`max-age`/`secure`/`httpOnly` are not propagated.
- **Compat**: additive DSL behavior addition → MINOR bump (v1.22.0). No signature change. No-op when the source attribute is missing.

## `org.galaxio.gatling.utils.RandomDataGenerators`

```scala
def randomValue[T](max: T)(implicit rng: RandomProvider[T]): T              // UNCHANGED
def randomValue[T](min: T, max: T)(implicit rng, ord): T                    // UNCHANGED
```

- **Before**: Scaladoc stated the maximum is inclusive.
- **After**: Scaladoc states the maximum is **exclusive** (matches the implementation). No behavior change.
- **Compat**: documentation only.

## Java/Kotlin facade

No facade file changes. `javaapi` delegations (`IntensityConverter`, `SimulationConfig.intensity`) already forward to Scala core (Constitution I).
