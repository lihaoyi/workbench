package scala.js


object Resource {
  def apply(path: String) = {
    Dynamic.global.ScalaJS.resources(path)
  }
}

class Resource(base64: String){
  lazy val string = Dynamic.global.atob(base64)
}