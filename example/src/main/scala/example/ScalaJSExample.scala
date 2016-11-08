package example
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import org.scalajs.dom.html
import scala.util.Random

case class Point(x: Int, y: Int){
  def +(p: Point) = Point(x + p.x, y + p.y)
  def /(d: Int) = Point(x / d, y / d)
}

@JSExport
object ScalaJSExample {
  val ctx =
    dom.document
       .getElementById("canvas")
       .asInstanceOf[html.Canvas]
       .getContext("2d")
       .asInstanceOf[dom.CanvasRenderingContext2D]

  var p = Point(128, 128)
  var color = "black"

  var enemiess = List.fill(10)(Point(util.Random.nextInt(255), util.Random.nextInt(255)))
  def clear() = {
    ctx.fillStyle = color
    ctx.fillRect(0, 0, 255, 255)
  }

  def run = {
    clear()
    ctx.fillStyle = "yellow"
    p = Point(p.x, (p.y + 2) % 255)
    ctx.fillRect(p.x, p.y, 5, 20)
    enemiess = for (enemy <- enemiess) yield {
      ctx.fillStyle = "red"
      ctx.fillRect(enemy.x, enemy.y, 10, 10)
      Point((enemy.x + 1) % 255, (enemy.y + 1) % 255)
    }
  }
  @JSExport
  def main(): Unit = {
    dom.window.setInterval(() => run, 10)
  }
}
