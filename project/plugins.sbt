// Every plugin here MUST publish an `_sbt2_3` artifact as well as `_2.12_1.0` — the build is
// cross-built on sbt 1 and sbt 2 (spec 012) and `addSbtPlugin` resolves the suffix per running
// major. `sbt-explicit-dependencies` is deliberately absent: it has no sbt 2 build, so it lives in
// the opt-in `project/hygiene/` overlay instead (research D-06).
addSbtPlugin("com.github.sbt"       % "sbt-ci-release"        % "1.12.0")
// sbt-ci-release 1.12.0 dropped its transitive sbt-git dependency; GitVersioning below needs it declared directly.
addSbtPlugin("com.github.sbt"       % "sbt-git"               % "2.1.0")
addSbtPlugin("io.gatling"           % "gatling-sbt"           % "4.19.1")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.19.0")
addSbtPlugin("org.scalameta"        % "sbt-scalafmt"          % "2.6.2")
addSbtPlugin("ch.epfl.scala"        % "sbt-scalafix"          % "0.14.7")
addSbtPlugin("com.typesafe"         % "sbt-mima-plugin"       % "1.1.6")
addSbtPlugin("org.scoverage"        % "sbt-scoverage"         % "2.4.4")
addSbtPlugin("pl.project13.scala"   % "sbt-jmh"               % "0.4.8")
addSbtPlugin("ch.epfl.scala"        % "sbt-bloop"             % "2.1.1")
