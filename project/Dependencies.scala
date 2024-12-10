import sbt.*

object Dependencies {
  lazy val gatlingCore: Seq[ModuleID] = Seq(
    "io.gatling" % "gatling-core",
    "io.gatling" % "gatling-core-java",
    "io.gatling" % "gatling-http",
    "io.gatling" % "gatling-http-java",
    "io.gatling" % "gatling-redis",
    "io.gatling" % "gatling-core-java",
    "io.gatling" % "gatling-redis-java",
  ).map(_ % "3.11.4" % Provided)

  lazy val fastUUID: Seq[ModuleID] = Seq(
    "com.eatthepath" % "fast-uuid" % "0.2.0" % Provided,
  )

  lazy val gatling: Seq[ModuleID] = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts",
    "io.gatling"            % "gatling-test-framework",
  ).map(_ % "3.11.4" % Test)

  lazy val json4s: Seq[ModuleID] = Seq(
    "org.json4s" %% "json4s-native"  % "4.1.0-M8",
    "org.json4s" %% "json4s-jackson" % "4.1.0-M8",
  )

  lazy val pureConfig: Seq[ModuleID] = Seq(
    "com.github.pureconfig" %% "pureconfig"      % "0.17.8",
    "com.github.pureconfig" %% "pureconfig-yaml" % "0.17.8",
  )

  lazy val jackson: Seq[ModuleID] = Seq(
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.18.2",
    "com.fasterxml.jackson.core"       % "jackson-core"            % "2.18.2",
  )

  lazy val scalaTest: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  )

  lazy val scalaCheck: Seq[ModuleID] = Seq(
    "org.scalacheck" %% "scalacheck" % "1.18.1" % "test",
  )

  lazy val scalaTestPlus: Seq[ModuleID] = Seq(
    "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % Test,
  )

  lazy val scalaMock: Seq[ModuleID] = Seq(
    "org.scalamock" %% "scalamock" % "5.2.0" % "test",
  )

  lazy val generex: Seq[ModuleID] = Seq(
    "com.github.mifmif" % "generex" % "1.0.2",
  )

  lazy val jwt: Seq[ModuleID] = Seq(
    "com.github.jwt-scala" %% "jwt-core" % "10.0.1",
  )

  lazy val circeDeps: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"    % "0.15.0-M1",
    "io.circe" %% "circe-generic" % "0.15.0-M1",
    "io.circe" %% "circe-parser"  % "0.15.0-M1",
    "io.circe" %% "circe-yaml"    % "1.15.0",
  )

  lazy val scalaTesting: Seq[ModuleID] = scalaCheck ++ scalaTest ++ scalaMock ++ scalaTestPlus

  // Add excludeAll netty to solve conflict run GatlinRunner with using Gatling 3.6.1 and io.netty:4.1.42.Final. Problem java.lang.NoSuchFieldError: DNT
  lazy val influxClientScala: Seq[ModuleID] = Seq(
    "io.razem" %% "scala-influxdb-client" % "0.6.3" excludeAll (
      ExclusionRule("io.netty", "netty-codec-http"),
      ExclusionRule("io.netty", "netty-buffer"),
      ExclusionRule("io.netty", "netty-codec-dns"),
      ExclusionRule("io.netty", "netty-codec-socks"),
      ExclusionRule("io.netty", "netty-codec"),
      ExclusionRule("io.netty", "netty-common"),
      ExclusionRule("io.netty", "netty-handler-proxy"),
      ExclusionRule("io.netty", "netty-handler"),
      ExclusionRule("io.netty", "netty-resolver-dns"),
      ExclusionRule("io.netty", "netty-resolver"),
      ExclusionRule("io.netty", "netty-transport")
    ),
  )

  lazy val junit: Seq[ModuleID] = Seq("org.junit.jupiter" % "junit-jupiter-engine" % "5.11.3" % Test)

}
