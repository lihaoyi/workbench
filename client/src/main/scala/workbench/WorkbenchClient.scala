package com.lihaoyi.workbench
import upickle._
import org.scalajs.dom
import org.scalajs.dom.extensions._
import upickle.{Reader, Writer, Js}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scalajs.concurrent.JSExecutionContext.Implicits.runNow
object WireServer extends autowire.Server[Js.Value, upickle.Reader, upickle.Writer]{

  def write[Result: Writer](r: Result) = upickle.writeJs(r)
  def read[Result: Reader](p: Js.Value) = upickle.readJs[Result](p)
  def wire(parsed: Js.Arr): Unit = {
    val Js.Arr(path, args: Js.Obj) = parsed

    val req = new Request(
      upickle.readJs[Seq[String]](path),
      args.value.toMap
    )
    WireServer.route[Api](WorkbenchClient).apply(req)
  }
}
@JSExport
object WorkbenchClient extends Api{
  val shadowBody = dom.document.body.cloneNode(deep = true)
  var interval = 1000
  var success = false
  @JSExport
  def main(bootSnippet: String, host: String, port: Int): Unit = {
    Ajax.post(s"http://$host:$port/notifications").onComplete{
      case util.Success(data) =>
        if (!success) println("Workbench connected")
        success = true
        interval = 1000
        json.read(data.responseText)
            .asInstanceOf[Js.Arr]
            .value
            .foreach(v => WireServer.wire(v.asInstanceOf[Js.Arr]))
        main(bootSnippet, host, port)
      case util.Failure(e) =>
        if (!success) println("Workbench disconnected " + e)
        success = false
        interval = math.min(interval * 2, 30000)
        dom.setTimeout(() => main(bootSnippet, host, port), interval)
    }
  }

  override def clear(): Unit = {
    dom.document.asInstanceOf[js.Dynamic].body = shadowBody.cloneNode(true)
    for(i <- 0 until 100000){
      dom.clearTimeout(i)
      dom.clearInterval(i)
    }
  }

  override def reload(): Unit = {
    dom.console.log("Reloading page...")
    dom.location.reload()
  }

  override def run(path: String, bootSnippet: Option[String]): Unit = {
    val tag = dom.document.createElement("script")
    var loaded = false

    tag.setAttribute("src", path)
    bootSnippet.foreach{ bootSnippet =>
      tag.onreadystatechange = (e: dom.Event) => {
        if (!loaded) {
          dom.console.log("Post-run reboot")
          js.eval(bootSnippet)
        }
        loaded = true
      }
      tag.asInstanceOf[js.Dynamic].onload = tag.onreadystatechange
    }
    dom.document.head.appendChild(tag)
  }

  override def print(level: String, msg: String): Unit = {
    level match {
      case "error" => dom.console.error(msg)
      case "warn" => dom.console.warn(msg)
      case "info" => dom.console.info(msg)
      case "log" => dom.console.log(msg)
    }
  }
}