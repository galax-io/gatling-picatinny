# Contract: NFR-YAML Assertions Public API (v1.18.0)

The compatibility-sensitive public surface this feature touches. Signatures are
**unchanged**; only observable correctness, error behavior, and deprecation status
change.

## Scala — `org.galaxio.gatling.assertions.AssertionsBuilder`

```
def assertionFromYaml(path: String): Iterable[Assertion]   // UNCHANGED signature
```
- **Deprecated** as of v1.18.0 (`@deprecated(<generic msg>, "1.18.0")`).
- Returns one `io.gatling.commons.stats.assertion.Assertion` per `value` entry across
  recognized records (no change for valid files).
- Unknown key → logs WARN naming the key, skips it (was: silent skip).
- Non-numeric value → `IllegalArgumentException` whose message contains the metric key
  and the offending value (was: bare `NumberFormatException`).
- Threshold types per metric: error-rate (`Процент ошибок`) = **Double** (error-rate
  parse widened `Int`→`Double`, so fractional percents like `5.5` now work instead of
  crashing); response-time percentile/max = **Int** (ms). Matches the Java facade.
- Cyrillic keys matched independent of `file.encoding` (toUtf round-trip removed).
- Test seam `private[assertions] def assertionsFrom(path)(implicit GatlingConfiguration)`
  remains (used by unit tests).

## Java — `org.galaxio.gatling.javaapi.Assertions`

```
public static List<Assertion> assertionFromYaml(String path)   // UNCHANGED signature
```
- **Deprecated** as of v1.18.0 (`@Deprecated(since = "1.18.0")` + Javadoc `@deprecated`).
- Returns exactly one `io.gatling.javaapi.core.Assertion` per `value` entry across
  recognized records — **no 2^n duplication** (was: grew ~2^n).
- Unknown key → logs WARN (SLF4J) naming the key, skips it.
- Non-numeric value → `AssertionBuilderException` whose message contains the key and the
  offending value.
- `ObjectMapper` is `static final`, modules registered once (no per-call init).
- Cyrillic keys matched independent of default charset (no-op toUtf removed).

### Equivalence contract (FR-002)
For identical input, the Java result and the Scala result are equivalent: **same count,
same per-entry `lt` threshold, same scope** (`all`→global, else detail by `group /
request`). Not object identity (distinct `Assertion` types). Verified by both sides
asserting the same expected normalized set.

## Java — `org.galaxio.gatling.javaapi.AssertionBuilderException` (public)

```
AssertionBuilderException(String msg, Throwable cause)   // UNCHANGED signature (package-private ctor)
public String msg();  public Throwable cause();          // UNCHANGED
```
- **FR-005**: ctor now calls `super(msg, cause)` → `getMessage()` returns `msg`,
  `getCause()` returns `cause` (were null). `.msg()`/`.cause()`, `equals`, `hashCode`,
  `toString` unchanged.
- **Deprecated** as of v1.18.0 (part of the NFR-YAML feature surface).

## NFR YAML format — UNCHANGED

No new keys, no strict-mode flag, no schema change. Same `nfr:` list of `{key, value}`
records. Existing `nfr.yml` files load to the same assertions (unknown keys still
skipped, now with a WARN).

## Compatibility statement

- PATCH-level correctness fixes + additive WARN + additive deprecation → **MINOR**
  (v1.18.0). No removals, no signature changes, no serialized-format change.
- Removal of the deprecated API is deferred to a future MAJOR once the replacement ships.
