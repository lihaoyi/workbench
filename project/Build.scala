import sbt._
import Keys._


object Build extends sbt.Build {
  import sbt._

  override lazy val projects = Seq(root)
  lazy val root =
    Project("scala-js-workbench", file("."))
      .dependsOn(uri("git://github.com/lihaoyi/SprayWebSockets.git"))
      .settings(
        sbtPlugin := true,
        (resources in Compile) := {(resources in Compile).value ++ (baseDirectory.value * "*.ts").get},
        resolvers += "spray repo" at "http://repo.spray.io",
        resolvers += "typesafe" at "http://repo.typesafe.com/typesafe/releases/",
        libraryDependencies ++= Seq(
          "io.spray" % "spray-can" % "1.2-RC3",
          "com.typesafe.akka"   %%  "akka-actor"    % "2.2.3",
          "com.typesafe.play" %% "play-json" % "2.2.0-RC1"
        )
      )
}