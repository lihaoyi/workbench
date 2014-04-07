package com.lihaoyi.workbench

import akka.actor.{ActorRef, Actor, ActorSystem}
import scala.concurrent.duration._

import play.api.libs.json.Json
import sbt._
import Keys._
import akka.actor.ActorDSL._
import com.typesafe.config.ConfigFactory
import play.api.libs.json.JsArray
import spray.http.{AllOrigins, HttpResponse}
import spray.routing.SimpleRoutingApp
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`

object Plugin extends sbt.Plugin with SimpleRoutingApp{
  implicit val system = ActorSystem(
    "SystemLol",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )

  val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  val updateBrowsers = taskKey[Unit]("partially resets some of the stuff in the browser")
  val localUrl = settingKey[(String, Int)]("localUrl")
  private[this] val routes = settingKey[Unit]("local websocket server")
  val bootSnippet = settingKey[String]("piece of javascript to make things happen")
  val updatedJS = taskKey[List[String]]("Provides the addresses of the JS files that have changed")

  val pubSub = actor(new Actor{
    var waitingActor: Option[ActorRef] = None
    var queuedMessages = List[JsArray]()
    case object Clear
    import system.dispatcher

    system.scheduler.schedule(0 seconds, 10 seconds, self, Clear)
    def respond(a: ActorRef, s: String) = {
      a ! HttpResponse(
        entity = s,
        headers = List(`Access-Control-Allow-Origin`(AllOrigins))
      )
    }
    def receive = (x: Any) => (x, waitingActor, queuedMessages) match {
      case (a: ActorRef, _, Nil) =>
        // Even if there's someone already waiting,
        // a new actor waiting replaces the old one
        waitingActor = Some(a)
      case (a: ActorRef, None, msgs) =>

        respond(a, JsArray(msgs).toString)
        queuedMessages = Nil
      case (msg: JsArray, None, msgs) =>
        queuedMessages = msg :: msgs
      case (msg: JsArray, Some(a), Nil) =>
        respond(a, Json.arr(msg).toString)
        waitingActor = None
      case (Clear, Some(a), Nil) =>
        respond(a, Json.arr().toString)
        waitingActor = None
    }
  })

  val workbenchSettings = Seq(
    localUrl := ("localhost", 12345),
    updatedJS := {
      var files: List[String] = Nil
      ((crossTarget in Compile).value * "*.js").get.map {
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
            if(level >= Level.Info) pubSub ! Json.arr("print", level.toString(), message)
          def success(message: => String) = pubSub ! Json.arr("print", "info", message)
          def trace(t: => Throwable) = pubSub ! Json.arr("print", "error", t.toString)
        }
      }
      clientLogger.setSuccessEnabled(true)
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      pubSub ! Json.arr("reload")
    },
    updateBrowsers := {
      val changed = updatedJS.value
      // There is no point in clearing the browser if no js files have changed.
      if (changed.length > 0) {
        pubSub ! Json.arr("clear")

        changed.map {
          path =>
            streams.value.log.info("workbench: Refreshing " + path)
            pubSub ! Json.arr(
              "run",
              path,
              bootSnippet.value
            )
        }
      }
    },
    routes := startServer(localUrl.value._1, localUrl.value._2){
      get{
        path("workbench.js"){
          complete{
            IO.readStream(
              getClass.getClassLoader
                      .getResourceAsStream("workbench_template.js")
            ).replace("<host>", localUrl.value._1)
             .replace("<port>", localUrl.value._2.toString)
             .replace("<bootSnippet>", bootSnippet.value)
          }
        } ~
        getFromDirectory(".")
      } ~
      post{
        path("notifications"){ ctx =>
          pubSub ! ctx.responder
        }
      }

    }
  )
}
