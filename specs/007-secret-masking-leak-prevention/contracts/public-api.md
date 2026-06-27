# Contract: Public & Internal Surface

This feature is compatibility-sensitive (published library). This contract records what stays stable and what is added.

## Unchanged public API (MUST stay green — Constitution II)

- `SigningKey` sealed trait + companion `object SigningKey`.
- `final case class StringSecret(value: String) extends SigningKey` — type, field `value: String`, `apply`/`unapply`/`copy`/`value` accessor all unchanged. **Only `toString` overridden.**
- `final case class AsymmetricKey(value: PrivateKey) extends SigningKey` — same; **only `toString` overridden.**
- `SimulationConfig` / Java facade `org.galaxio.gatling.javaapi.SimulationConfig` — all getters and types unchanged. No facade change.
- `jwt(...)` factory behavior (selects `StringSecret`/`AsymmetricKey`) unchanged.

**Compat note:** `toString` of a secret-holder is not a behavioral contract consumers may rely on; redacting it is safe under a MINOR bump (v1.23.0).

## Internal surface (not public API)

- `ConfigValueMasking`: visibility widened `private[config]` → `private[gatling]`. Still NOT public. Methods `isSensitive`, `displayValue` (kept), `displayConfig`, `redactUserInfo` (new).

## New serialized config surface (additive, optional — Constitution II)

`picatinny.redaction` block (HOCON), all optional:

```hocon
picatinny.redaction {
  additionalSensitiveKeys = ["myCorpToken", "internalPass"]   # merged with built-ins, never replaces
  replacement = "******"                                       # optional override of the sentinel
}
```

**Contract:**
- Absent block → built-in defaults; no behavior change for existing configs.
- `additionalSensitiveKeys` ADDS to the built-in floor; it cannot remove a built-in term.
- Terms are case-insensitive whole-word terms compared on a path's last segment — NOT regexes.

## Logging contract (FR-008/009/011)

- Diagnostics/banner output is emitted via SLF4J under category `org.galaxio.gatling.diagnostics`, one event per block.
- Recommended consumer config (docs + `examples/scala-sbt-example/src/test/resources/logback.xml`): a `BANNER` `ConsoleAppender` with `%msg%n` bound to that category with `additivity="false"`; suppress by setting the category level to `OFF`/`WARN`.
- The library ships NO `logback.xml`/`logback-test.xml` on its main classpath (FR-010).
