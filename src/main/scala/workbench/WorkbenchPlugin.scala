package com.lihaoyi.workbench
import sbt._
import sbt.Keys._
import autowire._
import org.scalajs.sbtplugin.ScalaJSPlugin

import scala.concurrent.ExecutionContext.Implicits.global

object WorkbenchPlugin extends AutoPlugin {

  override def requires = WorkbenchBasePlugin

  object autoImport {
    val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  }
  import autoImport._
  import WorkbenchBasePlugin.server
  import ScalaJSPlugin.AutoImport._

  val workbenchSettings = Seq(
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      server.value.Wire[Api].reload().call()
    },
    
    refreshBrowsers := refreshBrowsers.triggeredBy(fastOptJS in Compile).value
  )

  override def projectSettings = workbenchSettings

}
