package com.lihaoyi.workbench

import sbt._
import Keys._
import upickle._
object Plugin extends sbt.Plugin {

  val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  val updateBrowsers = taskKey[Unit]("partially resets some of the stuff in the browser")
  val localUrl = settingKey[(String, Int)]("localUrl")
  private[this] val server = settingKey[Server]("local websocket server")


  val bootSnippet = settingKey[String]("piece of javascript to make things happen")
  val updatedJS = taskKey[List[String]]("Provides the addresses of the JS files that have changed")



  val workbenchSettings = Seq(
    localUrl := ("localhost", 12345),
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
    updatedJS <<= (updatedJS, localUrl) map { (paths, localUrl) =>
      paths.map { path =>
        s"http://${localUrl._1}:${localUrl._2}$path"
      }
    },
    extraLoggers := {
      val clientLogger = FullLogger{
        new Logger {
          def log(level: Level.Value, message: => String) =
            if(level >= Level.Info) server.value msg Seq("print", level.toString, message)
          def success(message: => String) = server.value msg Seq("print", "info", message)
          def trace(t: => Throwable) = server.value msg Seq("print", "error", t.toString)
        }
      }
      clientLogger.setSuccessEnabled(true)
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      server.value msg Seq("reload")
    },
    updateBrowsers := {
      val changed = updatedJS.value
      // There is no point in clearing the browser if no js files have changed.
      if (changed.length > 0) {
        server.value msg Seq("clear")

        changed.foreach {
          path =>
            streams.value.log.info("workbench: Refreshing " + path)
            server.value msg Seq(
              "run",
              path,
              bootSnippet.value
            )
        }
      }
    },
    server := new Server(localUrl.value._1, localUrl.value._2, bootSnippet.value),
    (onUnload in Global) := { (onUnload in Global).value.compose{ state =>
      server.value.kill()
      state
    }}
  )
}
