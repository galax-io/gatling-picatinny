# Phase 0 Research: Secret Masking & Leak Prevention (v1.23.0)

Consolidated decisions for spec 007 (issues #208/#87/#88). Each decision: what was chosen, why, and what was rejected. No new dependencies — `scala-logging` (compile), Typesafe Config + PureConfig (present), logback (Test-scope transitive) cover everything.

## Repo facts that shaped the design

- `ConfigValueMasking` (in `config/ConfigValueMasking.scala`) is the **only** masking decision point today: `private[config] object`, two methods `displayValue(path, value): String` and `isSensitive(path): Boolean`, a hardcoded 17-token `Seq[String]`, matched by lowercase **`contains`** against the full path, sentinel `"******"`. Not config-extensible.
- The **only** call site is `SimulationConfigUtils.scala:51` (`logger.info(s"Simulation param for $path is set to: ${ConfigValueMasking.displayValue(path, value)}")`). The class uses `LazyLogging` (SLF4J). When a param is read as a nested `Config` (the `getConfig`/`ConfigTag` branch, ~line 68), `displayValue` receives the **whole block** with only the parent path checked → secret leaves inside a benign block print in clear. **This is the real #208 "config block leak".**
- `SigningKey.scala`: `sealed trait` with `final case class StringSecret(value: String)` and `final case class AsymmetricKey(value: PrivateKey)`. Default case-class `toString` leaks the secret / provider-dependent key bytes. No override today.
- `Diagnostics.scala:21` joins `mxBean.getInputArguments` raw → `-D` secrets echoed. `StartupBanner.scala:54` prints `baseUrl` raw. Four `println` sites total (`StartupBanner` 15/27/30, `Diagnostics` 13); zero `System.out/err`. Each block is already built as one string and printed in one `println` (single-event ready).
- `scala-logging` 3.9.6 is a **compile** dep, used repo-wide via `StrictLogging`/`LazyLogging` (e.g. `CookieParser`, `TransactionTracker`). No first-party logback; logback reaches **Test** only, transitively via Gatling. Test log capture idiom: `ListAppender` (`AssertionsBuilderSpec.captureWarns`).
- `examples/scala-sbt-example` is the only runnable overlay; it already ships `src/test/resources/logback.xml`. The Java/Kotlin overlays are non-runnable stubs (out of scope).

## D1 — Central redaction helper shape

**Decision.** Extend `ConfigValueMasking` into the single helper for ALL sinks. Keep `displayValue`/`isSensitive`; add `displayConfig` (D2), reuse `isSensitive` for JVM args (D4), add `redactUserInfo` (D5), make the term list injectable (D8). **Harden matching:** split path on `.`/`/`, test the **last segment only**, normalize into camelCase/snake/kebab words, require a token to equal a whole word (or a recognized `…Password/Secret/Token/Key` compound) — not raw `contains`. Add FR-003 terms (`authorization`, `bearer`, `passphrase`, `key`-as-compound). Keep the `"******"` sentinel. Widen `private[config]` → `private[gatling]`.

**Rationale.** One helper already exists and is the sole decision point — reuse satisfies FR-002 with no API churn. Word-boundary + last-segment kills the documented over-match (`tokenBucketSize`, `apiKeyboard`, `roleIdPrefix`) while keeping every currently-green assertion. Fixed-width sentinel reveals no entropy (OWASP: redact classified fields, never partial-reveal).

**Alternatives rejected.** New parallel helper (violates FR-002, two sources of truth); keep `contains` + add tokens (amplifies false positives — `passphrase` term would mask any `pass…`); regex token list (pushes over-match onto user config).

## D2 — Nested config leaf-walk masking

**Decision.** Add `displayConfig(cfg: com.typesafe.config.Config): String` that walks `cfg.entrySet().asScala` (already-flattened leaves), sorts by key, and for each leaf applies `displayValue(entry.getKey, entry.getValue.unwrapped)`, joining `s"$key = $shown"` with `\n`. At the single line-51 log site in `getValueByType`, when the read value is a `Config` (the `case ConfigTag` value-read), render via `displayConfig` instead of `displayValue`. Do **not** call `cfg.resolve()` on the display path (avoid materializing `${env}` secrets). `scala.jdk.CollectionConverters._` already imported.

**Rationale.** `Config.entrySet()` returns fully-flattened leaf paths with resolved values, so each leaf's last segment is testable via D1 — no manual recursion. Closes the benign-block-hides-secret-child hole (FR-004). Sorting → diff-stable output.

**Alternatives rejected.** `config.root().render()` raw (emits secrets — the current bug); manual `ConfigObject` recursion (`entrySet` already flattens; risks missing array/object leaves); rebuild masked `Config` via `withValue` + `render` (heavier; no current sink needs HOCON output).

## D3 — SigningKey / StringSecret toString redaction

**Decision.** Override `toString` ONLY: `StringSecret.toString = "StringSecret(******)"`, `AsymmetricKey.toString = "AsymmetricKey(******)"`. Do not rename types, change fields (`value: String`, `value: PrivateKey`), or drop case-class status.

**Rationale.** Default case-class `toString` leaks the raw HMAC secret and can leak provider-dependent key bytes (`AsymmetricKey`). Overriding `toString` is the minimal binary/source-compatible change — `value`, `apply`, `unapply`, `copy` all unchanged, so call sites and tests keep working.

**Alternatives rejected.** Make `value` private / rename (breaks API + `unapply`); convert to plain class (loses `apply/copy/unapply`); mask only `StringSecret` (`AsymmetricKey.value.toString` can also emit key material).

## D4 — `-D` JVM args redaction reusing the central list

**Decision.** In `Diagnostics.scala:21`, before joining, map each arg: for `-Dkey=value` (and `-Dkey:value`), split on the first `=`/`:`, strip leading `-D`, run the key through `ConfigValueMasking.isSensitive`; if sensitive replace the value with `"******"` keeping `-Dkey=******`. Non-`-D` args and value-less flags pass through. Same term set as D1.

**Rationale.** Leak originates at `getInputArguments`. Reusing `isSensitive` keeps a single list (FR-002); a config-extended term (D8) automatically covers JVM args. Preserving the key keeps ops debuggability while redacting only the value.

**Alternatives rejected.** Drop all `-D` args (loses non-secret flags); separate JVM-arg list (violates FR-002); regex over the whole joined string (fragile against quoting/spacing).

## D5 — URL userinfo stripping (API + fail-safe)

**Decision.** Add `private[gatling]` `redactUserInfo(raw: String): String`. Algorithm: (1) `try new java.net.URI(raw)`; (2) candidate userinfo = `Option(uri.getRawUserInfo) orElse Option(uri.getRawAuthority).filter(_.contains('@')).map(_.takeWhile(_ != '@'))`; empty → return `raw` unchanged; present → string-level replace of the exact `userinfo + "@"` run with `"******@"`; (3) on `URISyntaxException` / any `Throwable`, fall back to `raw.replaceFirst("://[^/@\\s]*@", "://******@")`, and if still no match return a conservative fully-redacted constant — NEVER throw, NEVER return the raw credential. Apply at `StartupBanner.scala:54`.

**Rationale.** `java.net.URI` single-arg parses `user:pass@host`; `getRawUserInfo` + `getRawAuthority` `@`-fallback catches registry-based authorities where `getUserInfo` returns null. String surgery preserves the original byte-for-byte except the credential — the 7-arg `URI` constructor re-quotes `%` and can re-encode reserved chars (JDK-8151244), unacceptable for a log line. Catch-all guarantees fail-safe (FR-007).

**Alternatives rejected.** 7-arg `URI` rebuild (double-encoding, not byte-faithful); `URI.create` (throws unchecked `IllegalArgumentException` — see `THttpClient.scala:111`); `getUserInfo` only (misses registry-based authorities); let exceptions propagate (violates fail-safe FR).

## D6 — println → SLF4J for StartupBanner + Diagnostics

**Decision.** Replace `println` in both with `extends StrictLogging` (in-repo idiom). Emit the WHOLE multi-line block in a SINGLE `logger.info(block)` call. Emitters live under `org.galaxio.gatling.diagnostics` → logger names fall under that category. Keep `isEnabled` flags gating emission (now gating a log call, not a print).

**Rationale.** One `logger.info(String)` = exactly one SLF4J/logback event, so the layout pattern applies once and embedded newlines pass through verbatim via `%msg` — alignment preserved (FR-009). A per-line loop re-applies the pattern N times and breaks alignment. `StrictLogging` names the logger by runtime FQN (confirmed in scala-logging 3.9.6 source); logback's dotted hierarchy binds a Scala object's `…StartupBanner$` to the package category. No new main dependency (FR-010).

**Alternatives rejected.** `println`/`Console.out` (the leak/uncategorized-output bug; unsuppressible by log config); `logger.info` per line (N events, alignment destroyed); drop the enable flags (regresses existing `DiagnosticsSpec`/`UtilityIntegrationSpec` and removes operator control).

## D7 — logback NOT on main classpath; recommended config via docs + overlay

**Decision.** Add NO logback to library main (compile logging dep stays `scala-logging`). Deliver recommended config (FR-011) via (1) docs and (2) the `examples/scala-sbt-example` overlay `logback.xml`: a dedicated `BANNER` `ConsoleAppender` with bare `<pattern>%msg%n</pattern>`, bound by `<logger name="org.galaxio.gatling.diagnostics" level="INFO" additivity="false"><appender-ref ref="BANNER"/></logger>` so the banner renders prefix-free, exactly once, and stays suppressible by setting that logger to `OFF`/`WARN`.

**Rationale.** A published library must not impose a backend (FR-010, SLF4J FAQ, constitution non-runnable/Provided). The dedicated category + `additivity="false"` prevents double output (event hitting both BANNER and root). Bare `%msg%n` is what makes the single-event banner render without prefixes — pairs with D6. Overlay is the FR-011-mandated delivery vehicle.

**Alternatives rejected.** Bundle logback + `logback.xml` in main (pollutes consumer classpath — `"Resource [logback.xml] occurs multiple times"`, FR-010 violation); banner category without `additivity="false"` (printed twice); docs-only without the overlay (fails FR-011's overlay requirement).

## D8 — Config-extensible masking list

**Decision.** Make the term list + placeholder injectable into `ConfigValueMasking`, loaded ONCE at config-load from an OPTIONAL block `picatinny.redaction` (`additionalSensitiveKeys: List[String] = Nil`, `replacement: String = "******"`), guarded by `config.hasPath` (the repo's `getOpt` idiom) so an absent block → built-in defaults (FR-012). Effective terms = built-in ++ user list, normalized/lowercased/de-duplicated — **MERGE, never replace** (users can only ADD to the floor). Resolve once, pass into the helper. Terms are case-insensitive whole-word terms (per D1), not regexes.

**Rationale.** PureConfig + typesafe-config already present (no new dep). Optional source + case-class defaults make absence safe. Merge-not-replace prevents configuring the library into leaking its own known secrets. Resolve-once keeps the hot logging path allocation-free.

**Alternatives rejected.** Replace-semantics (a user could delete `password`/`token` from coverage — unacceptable for a security feature); per-log-line config read (hot-path overhead); regex-valued config (re-introduces over-match D1 removes); no extensibility (fails FR-012).

## Sources

- SLF4J FAQ / error codes — libraries must not configure the backend; depend on `slf4j-api` at compile, backend at test.
- logback manual (Configuration, Layouts, Architecture) — `%msg` emits the whole message including newlines; pattern applied once per event; logger hierarchy + `additivity`.
- scala-logging 3.9.6 source (`Logging.scala`, `Logger.scala`) — `StrictLogging` logger named by `getClass.getName` (runtime FQN, trailing `$` for objects; bound by parent category).
- JDK 17 `java.net.URI` Javadoc + JDK-8151244 — `getRawUserInfo`/`getRawAuthority`, single-arg vs multi-arg constructor double-encoding.
- Typesafe Config Javadoc — `Config.entrySet()` returns flattened leaf entries; `ConfigRenderOptions`.
- PureConfig docs — optional sources / case-class defaults.
- OWASP Logging Cheat Sheet — redact classified fields, never log-and-pattern.
- In-repo: `ConfigValueMasking.scala`, `SimulationConfigUtils.scala`, `SigningKey.scala`, `Diagnostics.scala`, `StartupBanner.scala`, `VaultFeeder.scala:201`, `THttpClient.scala:111`, `AssertionsBuilderSpec.scala`, `project/Dependencies.scala`.
