package org.galaxio.gatling.testutil

import java.util.Locale

/** Runs a body with the JVM's default locale pinned — safe under ScalaTest's PARALLEL suite execution.
  *
  * `Locale.setDefault` mutates PROCESS-GLOBAL state, and this project does not set `Test / parallelExecution := false` at the
  * root (only `integration` does), so a suite flipping the default races every other suite in the JVM. Two things make it
  * deterministic here:
  *
  *   1. `synchronized` — serialises the save/set/restore so two windows cannot corrupt each other's restore, and `finally`
  *      restores even when the body throws.
  *   2. A bounded blast radius, established by enumeration rather than hope. Unlike a `ThreadLocal` side-channel, a locale
  *      window IS visible to concurrent threads, so what matters is whether any code a foreign suite runs would answer
  *      differently under it. The default-locale case-folding sites were enumerated: `ConfigValueMasking.splitWords` and
  *      `CookieParser` (both fixed to `Locale.ROOT` by this feature, hence locale-independent), `IntensityConverter`
  *      (`rps`/`rpm`/`rph`), `ClaimsBuilder` (`true`/`false`) and `VaultFeeder` (`http`, loopback names). None of the remaining
  *      tokens contains a character whose case mapping is locale-specific, so no foreign suite can observe the window.
  *
  * A forked test group would isolate more strongly, but it cannot be expressed here: sbt 2 requires `Def.uncached` for
  * `Test / testGrouping` and `sbt.Def.uncached` does not exist on sbt 1.13.0, so the setting breaks whichever major it is
  * written for. See `specs/014-perf-templates-cookies`.
  *
  * If point 2 ever stops holding — a new default-locale comparison over a token containing `i`/`I` — this fixture must be
  * revisited, not worked around.
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
}
