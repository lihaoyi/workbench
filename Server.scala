package com.lihaoyi.workbench

import akka.actor.{ActorRef, Actor, ActorSystem}
import com.typesafe.config.ConfigFactory
import sbt.IO
import spray.routing.SimpleRoutingApp
import akka.actor.ActorDSL._
import scala.Some
import upickle.{Writer, Json, Js}
import spray.http.{AllOrigins, HttpResponse}
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`
import concurrent.duration._
class Server(url: String, port: Int, bootSnippet: String) extends SimpleRoutingApp{
  implicit val system = ActorSystem(
    "SystemLol",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )

  val pubSub = actor(new Actor{
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

        respond(a, Json.write(Js.Array(msgs)))
        queuedMessages = Nil
      case (msg: Js.Array, None, msgs) =>
        queuedMessages = msg :: msgs
      case (msg: Js.Array, Some(a), Nil) =>
        respond(a, Json.write(Js.Array(Seq(msg))))
        waitingActor = None
      case (Clear, Some(a), Nil) =>
        respond(a, Json.write(Js.Array(Nil)))
        waitingActor = None
    }
  })

  startServer(url, port) {
    get {
      path("workbench.js") {
        complete {
          IO.readStream(
            getClass.getClassLoader
              .getResourceAsStream("workbench_template.js")
          ).replace("<host>", url)
            .replace("<port>", port.toString)
            .replace("<bootSnippet>", bootSnippet)
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

  def msg[T: Writer](t: T) = {
    pubSub ! upickle.writeJs(t)
  }
}