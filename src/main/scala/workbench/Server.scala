package com.lihaoyi.workbench

import akka.actor.{ActorRef, Actor, ActorSystem}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import sbt.{Logger, IO}
import spray.httpx.encoding.Gzip
import spray.routing.SimpleRoutingApp
import akka.actor.ActorDSL._

import upickle.Js
import upickle.default.{Reader, Writer}
import spray.http.{HttpEntity, AllOrigins, HttpResponse}
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`
import concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import org.scalajs.core.tools.io._
import org.scalajs.core.tools.logging.Level
import scala.tools.nsc
import scala.tools.nsc.Settings

import scala.tools.nsc.backend.JavaPlatform
import scala.tools.nsc.util.ClassPath.JavaContext
import scala.collection.mutable
import scala.tools.nsc.typechecker.Analyzer
import scala.tools.nsc.util.{JavaClassPath, DirectoryClassPath}
import spray.http.HttpHeaders._
import spray.http.HttpMethods._

class Server(url: String, port: Int) extends SimpleRoutingApp{
  val corsHeaders: List[ModeledHeader] =
    List(
      `Access-Control-Allow-Methods`(OPTIONS, GET, POST),
      `Access-Control-Allow-Origin`(AllOrigins),
      `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
      `Access-Control-Max-Age`(1728000)
    )

  implicit val system = ActorSystem(
    "Workbench-System",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )

  /**
   * The connection from workbench server to the client
   */
  object Wire extends autowire.Client[Js.Value, Reader, Writer] with ReadWrite{
    def doCall(req: Request): Future[Js.Value] = {
      longPoll ! Js.Arr(upickle.default.writeJs(req.path), Js.Obj(req.args.toSeq:_*))
      Future.successful(Js.Null)
    }
  }

  /**
   * Actor meant to handle long polling, buffering messages or waiting actors
   */
  private val longPoll = actor(new Actor{
    var waitingActors = List[ActorRef]()
    var queuedMessages = List[Js.Value]()
    var numActorsLastRespond: Int = 0

    /**
     * Flushes returns nothing to any waiting actor every so often,
     * to prevent the connection from living too long.
     */
    case object Clear
    import system.dispatcher

    system.scheduler.schedule(0.seconds, 10.seconds, self, Clear)

    def respond() = {
//      println(s"respond: #actors: ${waitingActors.size}, #msgs: ${queuedMessages.size}")
      val httpResponse = HttpResponse(
        headers = corsHeaders,
        entity = upickle.json.write(Js.Arr(queuedMessages:_*)))

      waitingActors.foreach(_ ! httpResponse)
      numActorsLastRespond = waitingActors.size
      waitingActors = Nil
      queuedMessages = Nil
    }

    def receive = (x: Any) => x match {
      case a: ActorRef =>
        waitingActors = a :: waitingActors
        // comparison to numActorsLastRespond increases the chance to reload all pages in case of multiple clients
        if (queuedMessages.nonEmpty && numActorsLastRespond > 0 && waitingActors.size >= numActorsLastRespond)
          respond()

      case msg: Js.Arr =>
        queuedMessages = msg :: queuedMessages
        if (waitingActors.nonEmpty) respond()

      case Clear => respond()
    }
  })

  /**
   * Simple spray server:
   *
   * - /workbench.js is hardcoded to be the workbench javascript client
   * - Any other GET request just pulls from the local filesystem
   * - POSTs to /notifications get routed to the longPoll actor
   */
  var hasBeenStarted: Boolean = false

  def start(): Unit = {
    if (hasBeenStarted) return
    hasBeenStarted = true
    startServer(url, port) {
      get {
        path("workbench.js") {
          complete {
            val body = IO.readStream(
              getClass.getClassLoader.getResourceAsStream("client-opt.js")
            )
            s"""
            (function(){
              $body

              com.lihaoyi.workbench.WorkbenchClient().main(${upickle.default.write(url)}, ${upickle.default.write(port)})
            }).call(this)
            """
          }
        } ~
        getFromDirectory(".")
      } ~
      post {
        path("notifications") { ctx =>
          longPoll ! ctx.responder
        }
      }
    }
  }

  def kill() = system.shutdown()

}
