import sbt.Keys._

lazy val root = project.in(file(".")).dependsOn(uri("git://github.com/lihaoyi/SprayWebSockets.git"))

name := "scala-js-workbench"

version := "0.1-SNAPSHOT"

organization := "com.lihaoyi.workbench"

sbtPlugin := true

(resources in Compile) := {(resources in Compile).value ++ (baseDirectory.value * "*.ts").get}

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "typesafe" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "io.spray" % "spray-can" % "1.2.0",
  "com.typesafe.akka"   %%  "akka-actor"    % "2.2.3",
  "com.typesafe.play" %% "play-json" % "2.2.0-RC1"
)
