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

  override def requires = ScalaJSPlugin

  object autoImport {
    val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
    val updatedJS = taskKey[List[String]]("Provides the addresses of the JS files that have changed")
    val spliceBrowsers = taskKey[Unit]("Attempts to do a live update of the code running in the browser while maintaining state")
    val localUrl = settingKey[(String, Int)]("localUrl")

    val sjs = inputKey[Unit]("Run a command via the sjs REPL, which compiles it to Javascript and runs it in the browser")
    val replFile = taskKey[File]("The temporary file which holds the source code for the currently executing sjs REPL")
    val sjsReset = taskKey[Unit]("Reset the currently executing sjs REPL")
  }
  import autoImport._
  import ScalaJSPlugin.AutoImport._

  val server = settingKey[Server]("local websocket server")

  lazy val replHistory = collection.mutable.Buffer.empty[String]

  val workbenchSettings = Seq(
    localUrl := ("localhost", 12345),
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
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      server.value.Wire[Api].reload().call()
    },
    // this currently requires the old <<= syntax
    // see https://github.com/sbt/sbt/issues/1444
    refreshBrowsers <<= refreshBrowsers.triggeredBy(fastOptJS in Compile),
    updatedJS := {
      var files: List[String] = Nil
      ((crossTarget in Compile).value * "*.js").get.foreach {
        (x: File) =>
          streams.value.log.info("workbench: Checking " + x.getName)
          FileFunction.cached(streams.value.cacheDirectory / x.getName, FilesInfo.lastModified, FilesInfo.lastModified) {
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
      val changed = updatedJS.value
      // There is no point in clearing the browser if no js files have changed.
      if (changed.length > 0) {
        for{
          path <- changed
          if !path.endsWith(".js.js")
        }{
          streams.value.log.info("workbench: Splicing " + path)
          val url = localUrl.value
          val prefix = s"http://${url._1}:${url._2}/"
          val s = munge(sbt.IO.read(new sbt.File(path.drop(prefix.length))))

          sbt.IO.write(new sbt.File(path.drop(prefix.length) + ".js"), s.getBytes)
          server.value.Wire[Api].run(path + ".js").call()
        }
      }
    },
    server := new Server(localUrl.value._1, localUrl.value._2),
    (onUnload in Global) := { (onUnload in Global).value.compose{ state =>
      server.value.kill()
      state
    }}
  ) ++ inConfig(Compile)(Seq(
    artifactPath in sjs := crossTarget.value / "repl.js",
    replFile := {
      val f = sourceManaged.value / "repl.scala"
      sbt.IO.write(f, replHistory.mkString("\n"))
      f
    },
    sources in Compile += replFile.value,
    sjs := Def.inputTaskDyn {
      import sbt.complete.Parsers._
      val str = sbt.complete.Parsers.any.*.parsed.mkString
      val newSnippet = s"""
          @scalajs.js.annotation.JSExport object O${replHistory.length}{
            $str
          };
          import O${replHistory.length}._
        """
      replHistory.append(newSnippet)
      Def.taskDyn {
        // Basically C&Ped from fastOptJS, since we dont want this
        // special mode from triggering updateBrowsers or similar
        val s = streams.value
        val output = (artifactPath in sjs).value

        val taskCache = WritableFileVirtualTextFile(s.cacheDirectory / "fastopt-js")

        sbt.IO.createDirectory(output.getParentFile)

        val relSourceMapBase =
          if ((relativeSourceMaps in fastOptJS).value)
            Some(output.getParentFile.toURI())
          else None

        // TODO: re-enable this feature for latest scalajs 
        // NOTE: maybe use 'scalaJSOptimizerOptions in fullOptJS'
        // (scalaJSOptimizer in fastOptJS).value.optimizeCP(
        //   (scalaJSPreLinkClasspath in fastOptJS).value,
        //   Config(
        //     output = WritableFileVirtualJSFile(output),
        //     cache = None,
        //     wantSourceMap = (emitSourceMaps in fastOptJS).value,
        //     relativizeSourceMapBase = relSourceMapBase,
        //     checkIR = (scalaJSOptimizerOptions in fastOptJS).value.checkScalaJSIR,
        //     disableOptimizer = (scalaJSOptimizerOptions in fastOptJS).value.disableOptimizer,
        //     batchMode = (scalaJSOptimizerOptions in fastOptJS).value.batchMode
        //     ),
        //   s.log
        // )
        // end of C&P
        val outPath = sbt.IO.relativize(
          baseDirectory.value,
          (artifactPath in sjs).value
        ).get

        sbt.IO.write(
          (artifactPath in sjs).value,
          sbt.IO.read(output) + s"\n\nO${replHistory.length - 1}()"
        )
        Def.task {
          server.value.Wire[Api].run(
            s"http://localhost:12345/$outPath"
          ).call()
          ()
        }
      }.dependsOn(packageJSDependencies, packageScalaJSLauncher, compile)
    },
    sjsReset := {
      println("Clearing sjs REPL History")
      replHistory.clear()
    },
    sjsReset := sjsReset.triggeredBy(fastOptJS)
  ))

  override def projectSettings = workbenchSettings

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
    for(char <- Seq("d", "c", "h", "i", "n", "m")){
      s = s.replaceAll("\n(ScalaJS\\." + char + "\\.[a-zA-Z_$0-9]+) = ", "\n$1 = $1 || ")
    }
    s
  }
}
