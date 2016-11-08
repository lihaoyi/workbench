import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {

  // jvm dependencies
  val sprayCan = "io.spray" % "spray-can" % "1.3.1"
  val sprayRouting = "io.spray" % "spray-routing" % "1.3.1"
  val akka = "com.typesafe.akka" %% "akka-actor" % "2.3.15"

  // js and shared dependencies
  val autowire = Def.setting("com.lihaoyi" %%% "autowire" % "0.2.6")
  val dom = Def.setting("org.scala-js" %%% "scalajs-dom" % "0.9.1")
  val upickle = Def.setting("com.lihaoyi" %%% "upickle" % "0.4.3")

}
