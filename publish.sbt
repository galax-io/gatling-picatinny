ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / organization         := "org.galaxio"
ThisBuild / organizationName     := "Galaxio Team"
ThisBuild / organizationHomepage := Some(uri("https://github.com/galax-io"))
ThisBuild / homepage             := Some(uri("https://github.com/galax-io/gatling-picatinny"))
ThisBuild / description          := "A Scala toolkit that extends the Gatling DSL with production-ready utilities (feeders, transactions, assertions, templates, config helpers, and a Redis integration) to build faster, more reliable performance tests."
ThisBuild / scmInfo              := Some(
  ScmInfo(
    uri("https://github.com/galax-io/gatling-picatinny"),
    "git@https://github.com/galax-io/gatling-picatinny.git",
  ),
)

ThisBuild / scalaVersion := "2.13.18"

ThisBuild / developers := List(
  Developer(
    id = "jigarkhwar",
    name = "Ioann Akhaltsev",
    email = "jigarkhwar88@gmail.com",
    url = uri("https://github.com/jigarkhwar"),
  ),
)

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
// sbt 2 types the URL-valued keys above as java.net.URI and `licenses` as Seq[License] (spec 015).
// `License.Apache2` emits the canonical SPDX id over https (spec 012, D-05); the parity gate diffs
// the resulting POM against the sbt 1 build.
ThisBuild / licenses             := Seq(License.Apache2)
