ThisBuild / versionScheme        := Some("semver-spec")
ThisBuild / organization         := "org.galaxio"
ThisBuild / organizationName     := "Galaxio Team"
ThisBuild / organizationHomepage := Some(url("https://github.com/galax-io"))
ThisBuild / homepage             := Some(url("https://github.com/galax-io/gatling-picatinny"))
ThisBuild / description          := "Library with a bunch of useful functions that extend Gatling DSL and make your performance better"
ThisBuild / scmInfo              := Some(
  ScmInfo(
    url("https://github.com/galax-io/gatling-picatinny"),
    "git@https://github.com/galax-io/gatling-picatinny.git",
  ),
)

ThisBuild / scalaVersion := "2.13.15"

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
ThisBuild / publishTo            := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository                 := "https://s01.oss.sonatype.org/service/local"

ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
