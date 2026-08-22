import sbt.*
import sbt.Keys.*
import explicitdeps.ExplicitDepsPlugin.autoImport.*

/** Accepted dependency-hygiene findings (#276, report-only — never CI-gated).
  *
  * These filters used to live in `build.sbt`. They moved here so the always-loaded build stays compilable under sbt 2, which
  * has no `sbt-explicit-dependencies` release (spec 012, D-06). Everything not filtered below is declared explicitly in
  * `project/Dependencies.scala`.
  *
  * `allRequirements` is required, not a shortcut: this plugin only exists while the overlay is attached via
  * `--addPluginSbtFile`, and a meta-build file cannot `enablePlugins` on the root project. With `noTrigger` the filters are
  * silently never applied and the report emits 9 findings instead of 2 — verified. `requires` pins ordering so these `-=`
  * settings land after ExplicitDepsPlugin has defined the keys.
  */
object HygieneFilters extends AutoPlugin {
  override def trigger  = allRequirements
  override def requires = explicitdeps.ExplicitDepsPlugin

  override def projectSettings: Seq[Setting[?]] = Seq(
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "com.chuusai",
      "shapeless*",
    ), // pureconfig-generic macro-expansion artifact, version governed by pureconfig
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "org.typelevel",
      "cats*",
    ), // pureconfig API surface leak, version governed transitively
    undeclaredCompileDependenciesFilter -= moduleFilter(
      "net.debasishg",
      "redisclient*",
    ), // version deliberately pinned by the gatling-redis umbrella (Provided host runtime)
    unusedCompileDependenciesFilter -= moduleFilter(
      "io.gatling",
      "gatling-redis*",
    ), // kept: pins redisclient to Gatling's own version
    unusedCompileDependenciesFilter -= moduleFilter(
      "org.openjdk.jmh",
      "jmh-generator*",
    ), // sbt-jmh code-generation-time dependency
  )
}
