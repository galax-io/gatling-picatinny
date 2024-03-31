ThisBuild / organization := "org.galaxio"
ThisBuild / scalaVersion := "2.13.10"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/galax-io/gatling-picatinny.git"),
    "git@https://github.com/galax-io/gatling-picatinny.git",
  ),
)

ThisBuild / description := "Gatling Utils"
ThisBuild / licenses    := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage    := Some(url("https://github.com/galax-io/gatling-picatinny.git"))
