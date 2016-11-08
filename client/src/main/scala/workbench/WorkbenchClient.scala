package com.lihaoyi.workbench
import upickle.Js
import upickle.default
import upickle.default.{Reader, Writer}
import upickle.json
import org.scalajs.dom
import org.scalajs.dom.ext._
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom.raw._

/**
 * The connection from workbench server to the client
 */
object Wire extends autowire.Server[Js.Value, Reader, Writer] with ReadWrite{
  def wire(parsed: Js.Arr): Unit = {
    val Js.Arr(path, args: Js.Obj) = parsed
    val req = new Request(default.readJs[Seq[String]](path), args.value.toMap)
    Wire.route[Api](WorkbenchClient).apply(req)
  }
}
@JSExport
object WorkbenchClient extends Api{
  @JSExport
  lazy val shadowBody = dom.document.body.cloneNode(deep = true)

  @JSExport
  var interval = 1000
  @JSExport
  var success = false
  @JSExport
  def main(host: String, port: Int): Unit = {
    def rec(): Unit = {
      Ajax.post(s"http://$host:$port/notifications").onComplete {
        case util.Success(data) =>
          if (!success) println("Workbench connected")
          success = true
          interval = 1000
          json.read(data.responseText)
            .asInstanceOf[Js.Arr]
            .value
            .foreach(v => Wire.wire(v.asInstanceOf[Js.Arr]))
          rec()
        case util.Failure(e) =>
          if (success) println("Workbench disconnected " + e)
          success = false
          interval = math.min(interval * 2, 30000)
          dom.window.setTimeout(() => rec(), interval)
      }
    }

    // Trigger shadowBody to get captured when the page first loads
    dom.window.addEventListener("load", (event: dom.Event) => {
      dom.console.log("Loading Workbench")
      shadowBody
      rec()
    })
  }
  @JSExport
  override def clear(): Unit = {
    dom.document.asInstanceOf[js.Dynamic].body = shadowBody.cloneNode(true)
    for(i <- 0 until 100000){
      dom.window.clearTimeout(i)
      dom.window.clearInterval(i)
    }
  }
  @JSExport
  override def reload(): Unit = {
    dom.console.log("Reloading page...")
    dom.window.location.reload()
  }
  @JSExport
  override def run(path: String): Unit = {
    val tag = dom.document.createElement("script").asInstanceOf[HTMLElement]
    tag.setAttribute("src", path)
    dom.document.head.appendChild(tag)
  }
  @JSExport
  override def print(level: String, msg: String): Unit = {
    level match {
      case "error" => dom.console.error(msg)
      case "warn" => dom.console.warn(msg)
      case "info" => dom.console.info(msg)
      case "log" => dom.console.log(msg)
    }
  }
}
