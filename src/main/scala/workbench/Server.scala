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

case class PromiseMessage(p: Promise[Js.Arr])

class WorkbenchActor extends Actor {
  private var waitingActors = List.empty[PromiseMessage]
  private var queuedMessages = List.empty[Js.Value]
  private var numActorsLastRespond = 0

  /**
    * Flushes returns nothing to any waiting actor every so often,
    * to prevent the connection from living too long.
    */
  case object Clear

  private val system = context.system
  import system.dispatcher

  system.scheduler.schedule(0.seconds, 10.seconds, self, Clear)

  private def respond(): Unit = {
    val messages = Js.Arr(queuedMessages: _*)
    waitingActors.foreach { a =>
      a.p.success(messages)
    }
    numActorsLastRespond = waitingActors.size
    waitingActors = Nil
    queuedMessages = Nil
  }

  override def receive = {
    case a: PromiseMessage =>
      // Even if there's someone already waiting,
      // a new actor waiting replaces the old one
      waitingActors = a :: waitingActors
      // comparison to numActorsLastRespond increases the chance to reload all pages in case of multiple clients
      if (queuedMessages.nonEmpty && numActorsLastRespond > 0 && waitingActors.size >= numActorsLastRespond)
        respond()

    case msg: Js.Arr =>
      queuedMessages = msg :: queuedMessages
      if (waitingActors.nonEmpty) respond()

    case Clear =>
      respond()
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

  var serverStarted = false

  def startServer(): Unit = {
    if (serverStarted) return
    serverStarted = true
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
        CustomDirectives.getFromBrowseableDirectories(rootDirectory.getOrElse(".")) ~
        post {
          path("notifications") {
            val p = Promise[Js.Arr]
            longPoll ! PromiseMessage(p)
            onSuccess(p.future) { v =>
              complete(HttpResponse(entity = HttpEntity(ContentType(MediaTypes.`application/json`), upickle.json.write(v))).withHeaders(corsHeaders: _*))
            }
          }
        }
    }

  def kill() = {
    system.terminate()
  }
}
