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
    WireServer.route[Api](Main).apply(
      autowire.Core.Request(
        parsed(0).asInstanceOf[Js.Arr].value.collect{case Js.Str(s) => s},
        parsed(1).value.asInstanceOf[Map[String, Js.Value]]
      )
    )
  }
}
@JSExport
object Main extends Api{
  def main(bootSnippet: String, host: String, port: Int): Unit = {
    def rec(): Unit = {
      val f = Ajax.get(s"http://$host:$port/notifications")

      f.onSuccess { case data =>
        val parsed = json.read(data.responseText).asInstanceOf[Js.Arr]
        WireServer.wire(parsed)
        rec()
      } (runNow)
      ()
    }
    rec()
  }


  override def clear(): Unit = ???

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
        dom.console.log("Post-run reboot")
        if (!loaded) {
          dom.console.log("Post-run reboot go!")
          js.eval(bootSnippet)
        }
        loaded = true
      }
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