package scala.js


object Resource {
  def apply(path: String) = {
    Dynamic.global.ScalaJS.resources.asInstanceOf[js.Dictionary].apply(path)
  }
  def create(value: String) = new Resource(value)
}

class Resource(base64: String){
  lazy val string = Dynamic.global.atob(base64)
}