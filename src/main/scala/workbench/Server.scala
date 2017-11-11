package com.lihaoyi.workbench

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.{Encoder, Gzip, NoCoding}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sbt.IO
import upickle.Js
import upickle.default.{Reader, Writer}

import scala.concurrent.{Future, _}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class PromiseString(p: Promise[String])

class WorkbenchActor extends Actor {
  private var waitingActor = Option.empty[PromiseString]
  private var queuedMessages = List.empty[Js.Value]

  /**
    * Flushes returns nothing to any waiting actor every so often,
    * to prevent the connection from living too long.
    */
  case object Clear

  private val system = context.system
  import system.dispatcher

  system.scheduler.schedule(0.seconds, 10.seconds, self, Clear)

  private def respond(a: PromiseString, s: String) = {
    a.p.success(s)
  }

  override def receive = (x: Any) => (x, waitingActor, queuedMessages) match {
    case (a: PromiseString, _, Nil) =>
      // Even if there's someone already waiting,
      // a new actor waiting replaces the old one
      waitingActor = Some(a)

    case (a: PromiseString, None, msgs) =>
      respond(a, upickle.json.write(Js.Arr(msgs: _*)))
      queuedMessages = Nil

    case (msg: Js.Arr, None, msgs) =>
      queuedMessages = msg :: msgs

    case (msg: Js.Arr, Some(a), Nil) =>
      respond(a, upickle.json.write(Js.Arr(msg)))
      waitingActor = None

    case (Clear, waitingOpt, msgs) =>
      waitingOpt.foreach(respond(_, upickle.json.write(Js.Arr(msgs: _*))))
      waitingActor = None
  }
}


class Server(
  url: String,
  port: Int,
  defaultRootObject: Option[String] = None,
  rootDirectory: Option[String] = None,
  useCompression: Boolean = false) {
  val corsHeaders: List[ModeledHeader] =
    List(
      `Access-Control-Allow-Methods`(OPTIONS, GET, POST),
      `Access-Control-Allow-Origin`(HttpOriginRange.*),
      `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
      `Access-Control-Max-Age`(1728000)
    )
  val cl = getClass.getClassLoader


  implicit val system = ActorSystem(
    "Workbench-System",
    config = ConfigFactory.load(cl),
    classLoader = cl
  )

  /**
    * The connection from workbench server to the client
    */
  object Wire extends autowire.Client[Js.Value, Reader, Writer] with ReadWrite {
    def doCall(req: Request): Future[Js.Value] = {
      longPoll ! Js.Arr(upickle.default.writeJs(req.path), Js.Obj(req.args.toSeq: _*))
      Future.successful(Js.Null)
    }
  }


  /**
    * Actor meant to handle long polling, buffering messages or waiting actors
    */
  private val longPoll = system.actorOf(Props[WorkbenchActor], "workbench-actor")

  /**
    * Simple spray server:
    *
    * - /workbench.js is hardcoded to be the workbench javascript client
    * - Any other GET request just pulls from the local filesystem
    * - POSTs to /notifications get routed to the longPoll actor
    */


  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end
  implicit val executionContext = system.dispatcher

  private val serverBinding = new AtomicReference[Http.ServerBinding]()
  private val encoder: Encoder = if (useCompression) Gzip else NoCoding

  startServer()

  def startServer() = {
    val bindingFuture = Http().bindAndHandle(
      handler = routes,
      interface = url,
      port = port,
      settings = ServerSettings(system))

    bindingFuture.onComplete {
      case Success(binding) ⇒
        //setting the server binding for possible future uses in the client
        serverBinding.set(binding)
        system.log.info(s"Server online at http://${binding.localAddress.getHostName}:${binding.localAddress.getPort}/")

      case Failure(cause) ⇒
    }
  }

  lazy val routes: Route =
    encodeResponseWith(encoder) {
      get {
        path("workbench.js") {
          val body = IO.readStream(
            getClass.getClassLoader.getResourceAsStream("client-opt.js")
          )

          complete(
            s"""
               |(function(){
               |  $body
               |
               |  WorkbenchClient.main(${upickle.default.write(url)}, ${upickle.default.write(port)})
               |}).call(this)
             """.stripMargin)
        }
      } ~
        pathSingleSlash {
          getFromFile(defaultRootObject.getOrElse(""))
        } ~
        getFromDirectory(rootDirectory.getOrElse(".")) ~
        post {
          path("notifications") {
            val p = Promise[String]
            longPoll ! PromiseString(p)
            onSuccess(p.future) { v =>
              complete(HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), v)).withHeaders(corsHeaders: _*))
            }
          }
        }
    }

  def kill() = {
    system.terminate()
  }
}
