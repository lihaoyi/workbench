package example
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom
import scala.util.Random

case class Point(x: Int, y: Int){
  def +(p: Point) = Point(x + p.x, y + p.y)
  def /(d: Int) = Point(x / d, y / d)
}

@JSExport
object ScalaJSExample {
  val ctx = dom.document
    .getElementById("canvas")
    .asInstanceOf[dom.HTMLCanvasElement]
    .getContext("2d")
    .asInstanceOf[dom.CanvasRenderingContext2D]

  var count = 0
  var p = Point(0, 0)
  val corners = Seq(Point(255, 255), Point(0, 255), Point(128, 0))

  def clear() = {
    ctx.fillStyle = "black"
    ctx.fillRect(0, 0, 255, 255)
  }

  def run = for (i <- 0 until 10){
    if (count % 30000 == 0) clear()
    count += 1
    p = (p + corners(Random.nextInt(3))) / 2
    val height = 512.0 / (255 + p.y)
    val r = (p.x * height).toInt
    val g = ((255-p.x) * height).toInt
    val b = p.y
    ctx.fillStyle = s"rgb($g, $r, $b)"

    ctx.fillRect(p.x, p.y, 1, 1)
  }
  @JSExport
  def main(): Unit = {
    dom.console.log("main")

    dom.setInterval(() => run, 50)
  }
}
