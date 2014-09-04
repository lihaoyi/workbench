package com.lihaoyi.workbench

import akka.actor.{ActorRef, Actor, ActorSystem}
import com.typesafe.config.ConfigFactory
import sbt.IO
import spray.routing.SimpleRoutingApp
import akka.actor.ActorDSL._

import upickle.{Reader, Writer, Js}
import spray.http.{AllOrigins, HttpResponse}
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`
import concurrent.duration._
import scala.concurrent.Future

class Server(url: String, port: Int, bootSnippet: String) extends SimpleRoutingApp{
  implicit val system = ActorSystem(
    "SystemLol",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )
  object Wire extends autowire.Client[Js.Value, upickle.Reader, upickle.Writer]{
    def doCall(req: Request): Future[Js.Value] = {
      pubSub ! Js.Arr(Js.Str(req.path.mkString(".")), Js.Obj(req.args.toSeq:_*))
      Future.successful(Js.Null)
    }
    def write[Result: Writer](r: Result) = upickle.writeJs(r)
    def read[Result: Reader](p: Js.Value) = upickle.readJs[Result](p)
  }
  private val pubSub = actor(new Actor{
    var waitingActor: Option[ActorRef] = None
    var queuedMessages = List[Js.Value]()
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

        respond(a, upickle.json.write(Js.Arr(msgs:_*)))
        queuedMessages = Nil
      case (msg: Js.Arr, None, msgs) =>
        queuedMessages = msg :: msgs
      case (msg: Js.Arr, Some(a), Nil) =>
        respond(a, upickle.json.write(Js.Arr(msg)))
        waitingActor = None
      case (Clear, Some(a), Nil) =>
        respond(a, upickle.json.write(Js.Arr()))
        waitingActor = None
    }
  })

  startServer(url, port) {
    get {
      path("workbench.js") {
        complete {
          IO.readStream(
            getClass.getClassLoader
              .getResourceAsStream("client-opt.js")
          ) + s"\nMain.main(${upickle.write(bootSnippet)}, ${upickle.write(url)}, ${upickle.write(port)})"
        }
      } ~
        getFromDirectory(".")
    } ~
      post {
        path("notifications") { ctx =>
          pubSub ! ctx.responder
        }
      }
  }
  def kill() = {
    system.shutdown()
  }
}