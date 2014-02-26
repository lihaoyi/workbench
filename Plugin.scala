package com.lihaoyi.workbench

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import akka.io
import akka.util.ByteString
import play.api.libs.json.JsArray
import java.nio.file.{Files, Paths}
import play.api.libs.json.Json
import spray.can.Http
import spray.can.server.websockets.model.Frame
import spray.can.server.websockets.model.OpCode
import spray.can.server.websockets.Sockets
import sbt._
import Keys._

import com.typesafe.config.ConfigFactory
import scala.collection.mutable
import akka.io.Tcp
import spray.http._
import spray.http.HttpHeaders.{`Access-Control-Allow-Origin`, Connection}
import spray.can.server.websockets.model.OpCode.Text
import spray.http.HttpRequest
import play.api.libs.json.JsArray
import spray.http.HttpResponse
import java.io.IOException

object Plugin extends sbt.Plugin {

  val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  val updateBrowsers = taskKey[Unit]("partially resets some of the stuff in the browser")
  val generateClient = taskKey[File]("generates a .js file that can be embedded in your web page")
  val localUrl = settingKey[(String, Int)]("localUrl")
  private[this] val server = settingKey[ActorRef]("local websocket server")
  val fileName = settingKey[String]("name of the generated javascript file")
  val bootSnippet = settingKey[String]("piece of javascript to make things happen")
 
  implicit val system = ActorSystem(
    "SystemLol",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )

  implicit class pimpedActor(server: ActorRef){
    def send(x: JsArray) = {
      server ! Frame(
        opcode = OpCode.Text,
        data = ByteString(x.toString())
      )
    }
  }

  val buildSettingsX = Seq(
    localUrl := ("localhost", 12345),
    fileName := "workbench.js",
    server := {
      implicit val server = system.actorOf(Props(new SocketServer()))
      val host = localUrl.value
      io.IO(Sockets) ! Http.Bind(server, host._1, host._2)
      server
    },

    extraLoggers := {
      val clientLogger = FullLogger{
        new Logger {
          def log(level: Level.Value, message: => String): Unit =
            if(level >= Level.Info) server.value.send(Json.arr("print", level.toString(), message))
          def success(message: => String): Unit = server.value.send(Json.arr("print", "info", message))
          def trace(t: => Throwable): Unit = server.value.send(Json.arr("print", "error", t.toString))
        }
      }
      clientLogger.setSuccessEnabled(true)
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    refreshBrowsers := {
      streams.value.log.info("workbench: Reloading Pages...")
      server.value.send(Json.arr("reload"))
    },
    updateBrowsers := {

      server.value send Json.arr("clear")
      ((crossTarget in Compile).value * "*.js").get.map{ (x: File) =>
        streams.value.log.info("workbench: Checking " + x.getName)
        FileFunction.cached(streams.value.cacheDirectory /  x.getName, FilesInfo.lastModified, FilesInfo.lastModified){ (f: Set[File]) =>
          streams.value.log.info("workbench: Refreshing " + x.getName)
          val cwd = Paths.get(new File("").getAbsolutePath)
          val filePath = Paths.get(f.head.getAbsolutePath)
          server.value send Json.arr(
            "run",
            "/" + cwd.relativize(filePath).toString,
            bootSnippet.value
          )
          f
        }(Set(x))
      }
    },
    generateClient := {
      FileFunction.cached(streams.value.cacheDirectory / "workbench"/ "workbench.js", FilesInfo.full, FilesInfo.exists){ (f: Set[File]) =>
        val transformed =
          IO.read(f.head)
            .replace("<host>", localUrl.value._1)
            .replace("<port>", localUrl.value._2.toString)
            .replace("<bootSnippet>", bootSnippet.value)
        val outputFile = (crossTarget in Compile).value / fileName.value
        IO.write(outputFile, transformed)
        Set(outputFile)
      }(Set(new File(getClass.getClassLoader.getResource("workbench_template.ts").toURI))).head
    }
  )

  class SocketServer() extends Actor{
    val sockets: mutable.Set[ActorRef] = mutable.Set.empty
    def receive = {
      case x: Tcp.Connected => sender ! Tcp.Register(self) // normal Http server init

      case req: HttpRequest =>
        // Upgrade the connection to websockets if you think the incoming
        // request looks good
        if (req.headers.contains(Connection("Upgrade"))){
          sender ! Sockets.UpgradeServer(Sockets.acceptAllFunction(req), self)
        }else{


          try{
            val data = Files.readAllBytes(
              Paths.get(req.uri.path.toString.drop(1))
            )
            val mimeType: ContentType = req.uri.path.toString.split('.').lastOption match {
              case Some("css") => MediaTypes.`text/css`
              case Some("html") => MediaTypes.`text/html`
              case Some("js") => MediaTypes.`application/javascript`
              case _ => ContentTypes.`text/plain`
            }
            sender ! HttpResponse(
              StatusCodes.OK,
              entity=HttpEntity.apply(mimeType, data),
              headers=List(
                `Access-Control-Allow-Origin`(spray.http.AllOrigins)
              )
            )
          }catch{case _: IOException =>
            sender ! HttpResponse(StatusCodes.NotFound)
          }
        }

      case Sockets.Upgraded =>
        sockets.add(sender)
        println("Browser Open n=" + sockets.size)
        self send Json.arr("boot")

      case f @ Frame(fin, rsv, Text, maskingKey, data) =>
        sockets.foreach(_ ! f.copy(maskingKey=None))

      case _: Tcp.ConnectionClosed =>
        if (sockets.contains(sender)) println("Browser Closed n=" + sockets.size )
        sockets.remove(sender)

      case x =>
    }
  }
}
