package scala.scalajs.js.resource

import sbt._
import Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._
import org.apache.commons.codec.binary.Base64


object Plugin extends sbt.Plugin {

  val resourceSettings = Seq(
    watchSources := {
      watchSources.value ++ (resources in Compile).value ++ Path.allSubpaths((sourceDirectory in Compile).value / "js").map(_._1).toSeq
    },
    packageJS := {
      val bundledJSResults: Set[sbt.File] = FileFunction.cached(
        cacheDirectory.value,
        FilesInfo.lastModified,
        FilesInfo.exists
      ){(inFiles: Set[File]) =>
        val pathMap = Path.relativeTo((resources in Compile).value)
        val bundle = crossTarget.value / "resources.js"
        val fileLines = for(file <- inFiles) yield {
          val b64 = Base64.encodeBase64String(IO.readBytes(file))
          "    \"" + pathMap(file).get + "\": \"" + b64 + "\""
        }
        IO.write(bundle, "\nScalaJS.resources = {\n" + fileLines.mkString(",\n") + "\n}" )
        Set(bundle)
      }(
        for{
          (resourceRoot: File) <- (resources in Compile).value.toSet
          (file, path) <- Path.allSubpaths(resourceRoot)
        } yield file
      )

      val copiedJSFiles = FileFunction.cached(
        cacheDirectory.value,
        FilesInfo.lastModified,
        FilesInfo.exists
      ){(inFiles: Set[File]) =>
        val pathMap = Path.relativeTo((sourceDirectory in Compile).value / "js")
        IO.copy(inFiles.map{f => f -> crossTarget.value / pathMap(f).get})
      }(
        for{
          (file, path) <- Path.allSubpaths((sourceDirectory in Compile).value / "js").toSet
        } yield file

      )
      val normalResults: Seq[sbt.File] = (packageJS in Compile).value

      normalResults ++ bundledJSResults ++ copiedJSFiles
    }
  )
}
