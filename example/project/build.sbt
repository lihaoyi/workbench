
addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.5.4")

lazy val root = project.in(file(".")).dependsOn(file("../.."))