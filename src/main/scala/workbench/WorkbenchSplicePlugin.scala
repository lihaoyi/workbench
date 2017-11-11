package com.lihaoyi.workbench

import sbt._
import sbt.Keys._
import autowire._
import org.scalajs.sbtplugin.ScalaJSPlugin

import scala.concurrent.ExecutionContext.Implicits.global

object WorkbenchSplicePlugin extends AutoPlugin {

  override def requires = WorkbenchPlugin

  object autoImport {
    val updatedJS = taskKey[List[String]]("Provides the addresses of the JS files that have changed")
    val spliceBrowsers = taskKey[Unit]("Attempts to do a live update of the code running in the browser while maintaining state")
  }
  import autoImport._
  import WorkbenchBasePlugin.autoImport._
  import WorkbenchBasePlugin.server
  import ScalaJSPlugin.AutoImport._

  val spliceSettings = Seq(
    updatedJS := {
      val streamsValue = streams.value

      var files: List[String] = Nil
      ((crossTarget in Compile).value * "*.js").get.foreach {
        (x: File) =>
          streamsValue.log.info("workbench: Checking " + x.getName)
          FileFunction.cached(streamsValue.cacheDirectory / x.getName, FilesInfo.lastModified, FilesInfo.lastModified) {
            (f: Set[File]) =>
              val fsPath = f.head.getAbsolutePath.drop(new File("").getAbsolutePath.length)
              files = fsPath :: files
              f
          }(Set(x))
      }
      files
    },
    updatedJS := {
      val paths = updatedJS.value
      val url = localUrl.value
      paths.map { path =>
        s"http://${url._1}:${url._2}$path"
      }
    },
    spliceBrowsers := {
      val streamsValue = streams.value

      val changed = updatedJS.value
      // There is no point in clearing the browser if no js files have changed.
      if (changed.nonEmpty) {
        for {
          path <- changed
          if !path.endsWith(".js.js")
        } {
          streamsValue.log.info("workbench: Splicing " + path)
          val url = localUrl.value
          val prefix = s"http://${url._1}:${url._2}/"
          val s = munge(sbt.IO.read(new sbt.File(path.drop(prefix.length))))

          sbt.IO.write(new sbt.File(path.drop(prefix.length) + ".js"), s.getBytes)
          server.value.Wire[Api].run(path + ".js").call()
        }
      }
    },
    spliceBrowsers := spliceBrowsers.triggeredBy(fastOptJS in Compile).value
  )

  override def projectSettings = spliceSettings

  def munge(s0: String) = {
    var s = s0
    s = s.replace("\nvar ScalaJS = ", "\nvar ScalaJS = ScalaJS || ")
    s = s.replaceAll(
      "\n(ScalaJS\\.c\\.[a-zA-Z_$0-9]+\\.prototype) = (.*?\n)",
      """
        |$1 = $1 || {}
        |(function(){
        |  var newProto = $2
        |  for (var attrname in newProto) { $1[attrname] = newProto[attrname]; }
        |})()
        |""".stripMargin
    )
    for (char <- Seq("d", "c", "h", "i", "n", "m")) {
      s = s.replaceAll("\n(ScalaJS\\." + char + "\\.[a-zA-Z_$0-9]+) = ", "\n$1 = $1 || ")
    }
    s
  }
}
