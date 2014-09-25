package com.lihaoyi.workbench
import sbt._
import sbt.Keys._
import autowire._
import upickle.Js
import scala.concurrent.ExecutionContext.Implicits.global
object Plugin extends sbt.Plugin {

  val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  val updateBrowsers = taskKey[Unit]("Partially resets some of the stuff in the browser")
  val spliceBrowsers = taskKey[Unit]("Attempts to do a live update of the code running in the browser while maintaining state")
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
    updateBrowsers := {
      val changed = updatedJS.value
      // There is no point in clearing the browser if no js files have changed.
      if (changed.length > 0) {
        server.value.Wire[Api].clear().call()

        changed.foreach { path =>
          streams.value.log.info("workbench: Refreshing " + path)
          server.value.Wire[Api].run(path, Some(bootSnippet.value)).call()
        }
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
          val prefix = "http://localhost:12345/"
          var s = IO.read(new sbt.File(path.drop(prefix.length)))

          s = s.replace("\nvar ScalaJS = ", "\nvar ScalaJS = ScalaJS || ")
          s = s.replaceAll("\n(ScalaJS\\.c\\.[a-zA-Z_$0-9]+\\.prototype) = ", "/*X*/\n$1 = $1 || ")
          for(char <- Seq("d", "c", "h", "i", "n", "m")){
            s = s.replaceAll("\n(ScalaJS\\." + char + "\\.[a-zA-Z_$0-9]+) = ", "\n$1 = $1 || ")
          }
          IO.write(new sbt.File(path.drop(prefix.length) + ".js"), s.getBytes)
          server.value.Wire[Api].run(path + ".js", None).call()
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
