package com.lihaoyi.workbench

import akka.actor.{ActorRef, Actor, ActorSystem}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import sbt.{Logger, IO}
import spray.httpx.encoding.Gzip
import spray.routing.SimpleRoutingApp
import akka.actor.ActorDSL._

import upickle.{Reader, Writer, Js}
import spray.http.{HttpEntity, AllOrigins, HttpResponse}
import spray.http.HttpHeaders.`Access-Control-Allow-Origin`
import concurrent.duration._
import scala.concurrent.Future
import scala.io.Source
import org.scalajs.core.tools.optimizer.{ScalaJSClosureOptimizer, ScalaJSOptimizer}
import org.scalajs.core.tools.io._
import org.scalajs.core.tools.logging.Level
import scala.tools.nsc
import scala.tools.nsc.Settings

import scala.tools.nsc.backend.JavaPlatform
import scala.tools.nsc.util.ClassPath.JavaContext
import scala.collection.mutable
import scala.tools.nsc.typechecker.Analyzer
import org.scalajs.core.tools.classpath.{CompleteClasspath, PartialClasspath}
import scala.tools.nsc.util.{JavaClassPath, DirectoryClassPath}

class Server(url: String, port: Int, bootSnippet: String) extends SimpleRoutingApp{
  implicit val system = ActorSystem(
    "Workbench-System",
    config = ConfigFactory.load(ActorSystem.getClass.getClassLoader),
    classLoader = ActorSystem.getClass.getClassLoader
  )

  /**
   * The connection from workbench server to the client
   */
  object Wire extends autowire.Client[Js.Value, upickle.Reader, upickle.Writer] with ReadWrite{
    def doCall(req: Request): Future[Js.Value] = {
      longPoll ! Js.Arr(upickle.writeJs(req.path), Js.Obj(req.args.toSeq:_*))
      Future.successful(Js.Null)
    }
  }

  /**
   * Actor meant to handle long polling, buffering messages or waiting actors
   */
  private val longPoll = actor(new Actor{
    var waitingActor: Option[ActorRef] = None
    var queuedMessages = List[Js.Value]()

    /**
     * Flushes returns nothing to any waiting actor every so often,
     * to prevent the connection from living too long.
     */
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

      case (Clear, waiting, Nil) =>
        waiting.foreach(respond(_, upickle.json.write(Js.Arr())))
        waitingActor = None
    }
  })

  /**
   * Simple spray server:
   *
   * - /workbench.js is hardcoded to be the workbench javascript client
   * - Any other GET request just pulls from the local filesystem
   * - POSTs to /notifications get routed to the longPoll actor
   */
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

            com.lihaoyi.workbench.WorkbenchClient().main(${upickle.write(bootSnippet)}, ${upickle.write(url)}, ${upickle.write(port)})
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
  def kill() = system.shutdown()

}