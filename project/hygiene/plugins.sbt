// Opt-in dependency-hygiene overlay (#276, spec 012 FR-006 / research D-06).
//
// `com.github.cb372:sbt-explicit-dependencies` publishes NO `_sbt2_3` artifact at any version, and
// `build.sbt` used to reference its keys symbolically — which made the whole build fail to COMPILE
// under sbt 2 with `Not found: undeclaredCompileDependenciesFilter`. So the plugin and its filters
// live here instead of in the always-loaded build, and are attached only when the report is run:
//
//   sbt --batch --addPluginSbtFile=project/hygiene/plugins.sbt \
//       undeclaredCompileDependencies unusedCompileDependencies
//
// sbt 1 ONLY. `--batch` is mandatory for non-TTY invocation. This file is a META-BUILD file, so it
// cannot itself carry the filters (they are project-scoped settings) — hence the AutoPlugin below,
// injected as a meta-build source. Files under project/hygiene/ are NOT compiled by a normal load.
addSbtPlugin("com.github.cb372" % "sbt-explicit-dependencies" % "0.3.1")

Compile / unmanagedSources += baseDirectory.value / "hygiene" / "HygieneFilters.scala"
