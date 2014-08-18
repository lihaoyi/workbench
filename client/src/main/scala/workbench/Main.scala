package workbench
import org.scalajs.dom.extensions._
import async.Async._
object Main{
  def main(bootSnippet: String, host: String, port: Int): Unit = async{
    while(true){
      val data = await(Ajax.get(s"http://$host:$port/notifications"))
    }
  }
}