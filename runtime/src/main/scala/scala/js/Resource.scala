package scala.js


object Resource {
  println("Resource")
  val fileDict = {
    val fileDict = Dynamic.global.ScalaJS.resources.asInstanceOf[js.Object]
    for(key <- Object.keys(fileDict)){
      val data = fileDict.asInstanceOf[js.Dictionary](key).asInstanceOf[String]
      fileDict.asInstanceOf[js.Dictionary](key) = new Resource(data).asInstanceOf[js.Any]
    }
    fileDict
  }

  println("Resource Initialized")
  def apply(path: String) = {
    Dynamic.global
           .ScalaJS
           .resources
           .asInstanceOf[js.Dictionary]
           .apply(path)
           .asInstanceOf[scala.js.Resource]
  }
  def create(value: String) = new Resource(value)
}

class Resource(base64: String){
  lazy val string = Dynamic.global.atob(base64).asInstanceOf[js.String]
}