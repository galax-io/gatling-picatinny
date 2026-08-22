ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / organization         := "org.galaxio"
ThisBuild / organizationName     := "Galaxio Team"
ThisBuild / organizationHomepage := Some(url("https://github.com/galax-io"))
ThisBuild / homepage             := Some(url("https://github.com/galax-io/gatling-picatinny"))
ThisBuild / description          := "A Scala toolkit that extends the Gatling DSL with production-ready utilities (feeders, transactions, assertions, templates, config helpers, and integrations like InfluxDB and Redis) to build faster, more reliable performance tests."
ThisBuild / scmInfo              := Some(
  ScmInfo(
    url("https://github.com/galax-io/gatling-picatinny"),
    "git@https://github.com/galax-io/gatling-picatinny.git",
  ),
)

ThisBuild / scalaVersion := "2.13.18"

ThisBuild / developers := List(
  Developer(
    id = "jigarkhwar",
    name = "Ioann Akhaltsev",
    email = "jigarkhwar88@gmail.com",
    url = url("https://github.com/jigarkhwar"),
  ),
)

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
// `License.Apache2` is the one form that compiles on BOTH sbt majors: sbt 1 types it as
// (String, URL) and sbt 2 as sbt.librarymanagement.License, matching each major's `licenses` key.
// It also emits the canonical SPDX id over https (spec 012, D-05).
ThisBuild / licenses             := List(License.Apache2)
