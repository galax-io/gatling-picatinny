import sbt.*

object Dependencies {
  private val GatlingVersion          = "3.13.5"
  lazy val gatlingCore: Seq[ModuleID] = Seq(
    "io.gatling" % "gatling-core",
    "io.gatling" % "gatling-core-java",
    "io.gatling" % "gatling-http",
    "io.gatling" % "gatling-http-java",
    "io.gatling" % "gatling-redis",
    "io.gatling" % "gatling-redis-java",
  ).map(_ % GatlingVersion % Provided)

  lazy val gatling: Seq[ModuleID] = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts",
    "io.gatling"            % "gatling-test-framework",
  ).map(_ % GatlingVersion % Test)

  lazy val fastUUID: Seq[ModuleID] = Seq(
    "com.eatthepath" % "fast-uuid" % "0.2.0" % Provided,
  )

  lazy val json4s: Seq[ModuleID] = Seq(
    "io.github.json4s" %% "json4s-native"  % "4.1.1",
    "io.github.json4s" %% "json4s-jackson" % "4.1.1",
  )

  lazy val pureConfig: Seq[ModuleID] = Seq(
    "com.github.pureconfig" %% "pureconfig"      % "0.17.10",
    "com.github.pureconfig" %% "pureconfig-yaml" % "0.17.10",
  )

  lazy val jackson: Seq[ModuleID] = Seq(
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.22.0",
    "com.fasterxml.jackson.core"       % "jackson-core"            % "2.22.0",
  )

  lazy val scalaLogging: Seq[ModuleID] = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
  )

  lazy val scalaTest: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % "3.2.20" % "test,it",
  )

  lazy val scalaCheck: Seq[ModuleID] = Seq(
    "org.scalacheck" %% "scalacheck" % "1.19.0" % "test,it",
  )

  lazy val scalaTestPlus: Seq[ModuleID] = Seq(
    "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % "test,it",
  )

  lazy val scalaMock: Seq[ModuleID] = Seq(
    "org.scalamock" %% "scalamock" % "7.5.5" % "test,it",
  )

  lazy val generex: Seq[ModuleID] = Seq(
    "com.github.mifmif" % "generex" % "1.0.2",
  )

  lazy val jwt: Seq[ModuleID] = Seq(
    "com.github.jwt-scala" %% "jwt-core" % "11.0.4",
  )

  // National-ID generation via libraries so Faker emits values with real, validator-passing checksums:
  // iban4j builds valid IBANs (ISO 7064 Mod 97-10); codice-fiscale builds valid Italian tax codes from a
  // Person + comune (correct control character). Runtime deps (used in main Faker). codice-fiscale pulls
  // Guava transitively. Authorized 2026-06-23.
  lazy val idValidation: Seq[ModuleID] = Seq(
    "org.iban4j"       % "iban4j"         % "3.2.11-RELEASE",
    "it.kamaladafrica" % "codice-fiscale" % "1.6.0",
  )

  lazy val circeDeps: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"    % "0.15.0-M1",
    "io.circe" %% "circe-generic" % "0.15.0-M1",
    "io.circe" %% "circe-parser"  % "0.15.0-M1",
    "io.circe" %% "circe-yaml"    % "1.15.0",
  )

  lazy val testcontainers: Seq[ModuleID] = Seq(
    "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.44.1" % "test,it",
  )

  // Real PostgreSQL JDBC driver — integration-test only (drives JdbcStorageBackend against a real
  // Postgres Testcontainers DB). Authorized 2026-06-21; MUST stay `it` scope, never published.
  lazy val jdbcDrivers: Seq[ModuleID] = Seq(
    "org.postgresql" % "postgresql" % "42.7.11" % "it",
  )

  lazy val scalaTesting: Seq[ModuleID] =
    scalaCheck ++ scalaTest ++ scalaMock ++ scalaTestPlus ++ testcontainers ++ jdbcDrivers

  lazy val junit: Seq[ModuleID] = Seq(
    "org.junit.jupiter"    % "junit-jupiter"     % "6.1.0"  % "test,it",
    "com.github.sbt.junit" % "jupiter-interface" % "0.19.0" % "test,it",
    "org.assertj"          % "assertj-core"      % "3.27.7" % "test,it",
  )

}
