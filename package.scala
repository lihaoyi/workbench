package scala.js

import akka.actor.{Props, ActorRef, Actor, ActorSystem}
import akka.io
import akka.util.ByteString
import play.api.libs.json.JsArray

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
import spray.http.{StatusCodes, HttpResponse, HttpRequest}
import spray.http.HttpHeaders.Connection
import spray.can.server.websockets.model.OpCode.Text

package object workbench extends sbt.Plugin {
  val refreshBrowsers = taskKey[Unit]("Sends a message to all connected web pages asking them to refresh the page")
  val generateClient = taskKey[File]("generates a .js file that can be embedded in your web page")
  val localUrl = settingKey[(String, Int)]("localUrl")
  val server = settingKey[ActorRef]("local websocket server")
  val fileName = settingKey[String]("name of the generated javascript file")

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
      implicit val server = system.actorOf(Props(new SocketServer))
      val host = localUrl.value
      io.IO(Sockets) ! Http.Bind(server, host._1, host._2)
      server
    },

    extraLoggers := {
      val clientLogger = FullLogger{
        new Logger {
          def log(level: Level.Value, message: => String): Unit =
            if(level >= Level.Info) server.value.send(Json.arr("print", level.toString(), message))
          def success(message: => String): Unit = server.value.send(Json.arr("print", message))
          def trace(t: => Throwable): Unit = server.value.send(Json.arr("print", t.toString))
        }
      }
      val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => clientLogger +: currentFunction(key)
    },
    refreshBrowsers := {
      streams.value.log("Reloading Pages...")
      server.value.send(Json.arr("reload"))
    },
    generateClient := {
      val clientTemplate = IO.readStream(getClass.getClassLoader.getResourceAsStream("workbench_template.ts"))
      val transformed = clientTemplate.replace("<host>", localUrl.value._1).replace("<port>", localUrl.value._2.toString)
      val outputFile = (crossTarget in Compile).value / fileName.value
      IO.write(outputFile, transformed)
      outputFile
    }
  )

  class SocketServer extends Actor{
    val sockets: mutable.Set[ActorRef] = mutable.Set.empty
    def receive = {
      case x: Tcp.Connected => sender ! Tcp.Register(self) // normal Http server init

      case req: HttpRequest =>
        // Upgrade the connection to websockets if you think the incoming
        // request looks good
        if (req.headers.contains(Connection("Upgrade"))){
          sender ! Sockets.UpgradeServer(Sockets.acceptAllFunction(req), self)
        }else{
          sender ! HttpResponse(
            StatusCodes.OK,
            entity="i am a cow"
          )
        }

      case Sockets.Upgraded =>
        sockets.add(sender)
        println("Browser Open n=" + sockets.size)

      case f @ Frame(fin, rsv, Text, maskingKey, data) =>
        sockets.foreach(_ ! f.copy(maskingKey=None))

      case _: Tcp.ConnectionClosed =>
        if (sockets.contains(sender)) println("Browser Closed n=" + sockets.size )
        sockets.remove(sender)


      case x =>
    }
  }
}
