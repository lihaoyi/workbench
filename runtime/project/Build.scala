import sbt._
import Keys._
import ch.epfl.lamp.sbtscalajs.ScalaJSPlugin._
import ScalaJSKeys._

object Build extends sbt.Build {
  lazy val resource =
    project.in(file("."))
           .settings(scalaJSSettings: _*)
           .settings(name := "resources")
}