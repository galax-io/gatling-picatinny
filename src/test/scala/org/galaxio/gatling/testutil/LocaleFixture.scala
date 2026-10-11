package org.galaxio.gatling.testutil

import java.util.Locale

/** Runs a body with the JVM's default locale pinned — safe under ScalaTest's PARALLEL suite execution.
  *
  * `Locale.setDefault` mutates PROCESS-GLOBAL state, and this project does not set `Test / parallelExecution := false` at the
  * root (only `integration` does), so a suite flipping the default races every other suite in the JVM. Unlike a `ThreadLocal`
  * side-channel, a window IS visible to every concurrent thread, so what makes it deterministic is that both sides of the race
  * take the same lock:
  *
  *   1. Writers: [[withLocale]] is `synchronized`, which serialises the save/set/restore so two windows cannot corrupt each
  *      other's restore; `finally` restores even when the body throws.
  *   2. Readers: a suite whose expectations only hold under the ambient locale runs inside [[withStableDefault]], which takes
  *      the same lock without changing the default, so no window can open or close on another thread while it runs.
  *
  * Which suites are readers follows from the production code they exercise: any code that consults the default locale answers
  * differently inside a window, and a window that opens between two reads of the default (a production call and the reference
  * it is compared with) makes them disagree. The case-folding sites, from
  * `grep -rnE "toLowerCase|toUpperCase" src/main | grep -v Locale.ROOT`:
  *
  *   - `IntensityConverter` (`rps`/`rpm`/`rph`), `ClaimsBuilder` (`true`/`false`), `VaultFeeder` (`http`, loopback names) and
  *     `WorkloadTimeline` (`maxperformance`): none of these tokens contains a character whose case mapping is locale-specific,
  *     so a window cannot change their answer.
  *   - `Faker`: a window DOES change it. `internet.email`, `internet.username` and `person.companyEmailName` lower-case
  *     arbitrary names (`İ` lowers to `i` under Turkish but to `i` + U+0307 elsewhere, and an ASCII `I` to a dotless `ı`); the
  *     postal-code (CA), BIC and passport-number generators upper-case random letters (`i` becomes `İ`); `Country.custom`
  *     upper-cases its ISO code. Its suite, `GeneratedFeederSpec`, is therefore a reader for every test.
  *
  * `ConfigValueMasking.splitWords` and `CookieParser` use `Locale.ROOT` and are independent of the default; the Turkish suites
  * exist to prove that. Number formatting that follows the default (`Formatters.bytes` uses `f"%.2f"`) is a site of the same
  * kind that no test asserts on today.
  *
  * A forked test group would isolate more strongly, but it cannot be expressed here: sbt 2 requires `Def.uncached` for
  * `Test / testGrouping` and `sbt.Def.uncached` does not exist on sbt 1.13.0, so the setting breaks whichever major it is
  * written for. See `specs/014-perf-templates-cookies`.
  *
  * If a new default-locale site appears, either the suite that asserts on it becomes a reader (point 2) or the site moves to
  * `Locale.ROOT`, which changes production behaviour. Do not work around it by widening the fixture.
  */
object LocaleFixture {

  /** Turkish: the case mapping that makes `I` lower to a dotless `ı` and `İ` lower to `i`, which is what breaks ASCII-assuming
    * `toLowerCase` comparisons.
    */
  val Turkish: Locale = Locale.forLanguageTag("tr-TR")

  /** Run `body` with `locale` as the JVM default, restoring the previous default afterwards. */
  def withLocale[A](locale: Locale)(body: => A): A = synchronized {
    val previous = Locale.getDefault
    Locale.setDefault(locale)
    try body
    finally Locale.setDefault(previous)
  }

  /** Convenience for the case every caller in this repository wants. */
  def withTurkish[A](body: => A): A = withLocale(Turkish)(body)

  /** Run `body` with the JVM default locale held exactly as it is: no [[withLocale]] window can open or close on another thread
    * until `body` returns, so every read of the default inside it sees one value.
    *
    * It does not change the default, and it is re-entrant: a window opened by `body`'s own thread still applies. Do not wait
    * inside `body` for another thread that opens a window — it is queued on this lock until `body` returns.
    */
  def withStableDefault[A](body: => A): A = synchronized(body)
}
