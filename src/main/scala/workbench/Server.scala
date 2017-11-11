package com.lihaoyi.workbench

import akka.actor.ActorDSL._
import akka.actor.{Actor, ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import sbt.IO
import spray.http.HttpHeaders.{`Access-Control-Allow-Origin`, _}
import spray.http.HttpMethods._
import spray.http.{AllOrigins, HttpResponse}
import spray.routing.SimpleRoutingApp
import upickle.Js
import upickle.default.{Reader, Writer}

import scala.concurrent.Future
import scala.concurrent.duration._

class Server(url: String, port: Int, defaultRootObject: Option[String] = None, rootDirectory: Option[String] = None) extends SimpleRoutingApp{
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
    var waitingActors = List.empty[ActorRef]
    var queuedMessages = List.empty[Js.Value]
    var numActorsLastRespond: Int = 0

    /**
     * Flushes returns nothing to any waiting actor every so often,
     * to prevent the connection from living too long.
     */
    case object Clear
    import system.dispatcher

    system.scheduler.schedule(0.seconds, 10.seconds, self, Clear)

    def respond(): Unit = {
//      println(s"respond: #actors: ${waitingActors.size}, #msgs: ${queuedMessages.size}")
      val httpResponse = HttpResponse(
        headers = corsHeaders,
        entity = upickle.json.write(Js.Arr(queuedMessages:_*)))

      waitingActors.foreach(_ ! httpResponse)
      numActorsLastRespond = waitingActors.size
      waitingActors = Nil
      queuedMessages = Nil
    }

    override def receive: Receive = {
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
          pathSingleSlash {
            getFromFile(defaultRootObject.getOrElse(""))
          } ~
          getFromDirectory(rootDirectory.getOrElse("."))
      } ~ post {
          path("notifications") { ctx =>
            longPoll ! ctx.responder
          }
        }
    }
  }

  def kill() = system.shutdown()

}
