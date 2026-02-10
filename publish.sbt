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
ThisBuild / licenses             := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
