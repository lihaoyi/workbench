package scala.js.resource

import sbt._
import Keys._

object Plugin extends sbt.Plugin {
  val myTask = taskKey[Set[File]]("prepares resources")
  val resourceSettings = Seq(
    watchSources := {
      watchSources.value ++ (resources in Compile).value
    },

    myTask := {
      val fileData = for{
        resourceRoot <- (resources in Compile).value
        (file, path) <- Path.allSubpaths(resourceRoot)
      } yield {
        path -> new sun.misc.BASE64Encoder().encode(IO.readBytes(file)).replace("\n", "").replace("\r", "")
      }

      val bundle = crossTarget.value / "resources.js"
      val fileLines = for((path, data) <- fileData) yield { "    \"" + path + "\": ScalaJS.Resource(\"" + data + "\")" }


      IO.write(bundle, "\nScalaJS.resources = {\n" + fileLines.mkString(",\n") + "\n}" )

      Set(bundle)
    }
  )
}
