package scala.js.resource

import sbt._
import Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import org.apache.commons.codec.binary.Base64


object Plugin extends sbt.Plugin {

  val resourceSettings = Seq(
    watchSources := {
      watchSources.value ++ (resources in Compile).value
    },
    packageJS := {

      val fileData = for{
        resourceRoot <- (resources in Compile).value
        (file, path) <- Path.allSubpaths(resourceRoot)
      } yield {
        val b64 = Base64.encodeBase64String(IO.readBytes(file))
        path -> b64
      }

      val bundle = crossTarget.value / "resources.js"
      val fileLines = for((path, data) <- fileData) yield {
        "    \"" + path + "\": \"" + data + "\""
      }

      IO.write(bundle, "\nScalaJS.resources = {\n" + fileLines.mkString(",\n") + "\n}" )

      (packageJS in Compile).value :+ bundle
    }
  )
}
