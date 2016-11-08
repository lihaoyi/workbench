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

}
