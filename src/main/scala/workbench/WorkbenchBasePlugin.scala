package com.lihaoyi.workbench
import scala.concurrent.ExecutionContext.Implicits.global
import sbt._
import sbt.Keys._
import autowire._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.core.tools.io._
import org.scalajs.sbtplugin.ScalaJSPluginInternal._
import org.scalajs.sbtplugin.Implicits._

object WorkbenchBasePlugin extends AutoPlugin {

  override def requires = ScalaJSPlugin

  object autoImport {
    sealed trait StartMode

    object WorkbenchStartModes {
      case object OnCompile extends StartMode
      case object OnSbtLoad extends StartMode
      case object Manual extends StartMode
    }

    val localUrl = settingKey[(String, Int)]("localUrl")
    val workbenchDefaultRootObject = settingKey[Option[(String, String)]]("path to defaultRootObject served on `/` and rootDirectory")
    val workbenchStartMode = settingKey[StartMode](
      "should the web server start on sbt load, on compile, or only by manually running `startWorkbenchServer`")
    val startWorkbenchServer = taskKey[Unit]("start local web server manually")
  }
  import autoImport._
  import WorkbenchStartModes._
  import ScalaJSPlugin.AutoImport._

  val server = settingKey[Server]("local websocket server")

  lazy val replHistory = collection.mutable.Buffer.empty[String]

  val workbenchSettings = Seq(
    localUrl := ("localhost", 12345),
    workbenchDefaultRootObject := None,
    (extraLoggers in ThisBuild) := {
      val clientLogger = FullLogger{
        new Logger {
          def log(level: Level.Value, message: => String) =
            if(level >= Level.Info) server.value.Wire[Api].print(level.toString, message).call()
          def success(message: => String) = server.value.Wire[Api].print("info", message).call()
          def trace(t: => Throwable) = server.value.Wire[Api].print("error", t.toString).call()
        }
      }
      clientLogger.setSuccessEnabled(true)
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    server := {
      val server = new Server(localUrl.value._1, localUrl.value._2, workbenchDefaultRootObject.value.map(_._1), workbenchDefaultRootObject.value.map(_._2))
      if (workbenchStartMode.value == OnSbtLoad) server.start()
      server
    },
    workbenchStartMode := OnSbtLoad,
    startWorkbenchServer := server.value.start(),
    (compile in Compile) := (compile in Compile)
      .dependsOn(
        Def.task {
          if (workbenchStartMode.value == OnCompile) server.value.start()
        })
      .value,
    (onUnload in Global) := { (onUnload in Global).value.compose{ state =>
      server.value.kill()
      state
    }}
  )

  override def projectSettings = workbenchSettings

}
