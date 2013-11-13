package scala.scalajs
package js


object Resource {
  println("Resource")
  val fileDict = {
    val fileDict = js.Dynamic.global.ScalaJS.resources.asInstanceOf[js.Object]
    for(key <- js.Object.keys(fileDict)){
      val data = fileDict.asInstanceOf[js.Dictionary](key).asInstanceOf[String]
      fileDict.asInstanceOf[js.Dictionary](key) = new Resource(data).asInstanceOf[js.Any]
    }
    fileDict
  }

  println("Resource Initialized")
  def apply(path: String) = {
    js.Dynamic.global
              .ScalaJS
              .resources
              .asInstanceOf[js.Dictionary]
              .apply(path)
              .asInstanceOf[Resource]
  }
  def create(value: String) = new Resource(value)
}

class Resource(base64: String){
  lazy val string = js.Dynamic.global.atob(base64).asInstanceOf[js.String]
}