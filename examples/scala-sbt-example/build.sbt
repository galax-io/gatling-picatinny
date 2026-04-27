enablePlugins(GatlingPlugin)

ThisBuild / organization := "org.galaxio.performance"
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version      := "0.1.0"

name := "scala-sbt-example"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.galaxio"          %% "gatling-picatinny"         % sys.props.getOrElse("picatinny.version", "0.0.0-ci-local") % Test,
  "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.11.5"                                                   % Test,
  "io.gatling"            % "gatling-test-framework"    % "3.11.5"                                                   % Test,
)

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
)
