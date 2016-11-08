package com.lihaoyi.workbench
import scala.concurrent.ExecutionContext.Implicits.global
import sbt._
import sbt.Keys._
import autowire._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.core.tools.io._
import org.scalajs.sbtplugin.ScalaJSPluginInternal._
import org.scalajs.sbtplugin.Implicits._

object WorkbenchPlugin extends AutoPlugin {

  override def requires = WorkbenchBasePlugin

  object autoImport {
    val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  }
  import autoImport._
  import WorkbenchBasePlugin.autoImport._
  import WorkbenchBasePlugin.server
  import ScalaJSPlugin.AutoImport._

  val workbenchSettings = Seq(
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      server.value.Wire[Api].reload().call()
    },
    // this currently requires the old <<= syntax
    // see https://github.com/sbt/sbt/issues/1444
    refreshBrowsers <<= refreshBrowsers.triggeredBy(fastOptJS in Compile)
  )

  override def projectSettings = workbenchSettings

}
