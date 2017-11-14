package com.lihaoyi.workbench


import java.io.File

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.BasicDirectives.{extractLog, extractUnmatchedPath}
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.DirectoryRenderer
import akka.http.scaladsl.server.directives.RouteDirectives.reject

import scala.annotation.tailrec


/**

  Akka was made more secure via
    https://github.com/akka/akka-http/issues/365
    https://github.com/akka/akka-http/issues/365

  Which made it no longer follow symlinks, which for a developer server is a bit too secure.  So this copies blatantly from akka http's
    https://github.com/akka/akka-http/blob/master/akka-http/src/main/scala/akka/http/scaladsl/server/directives/FileAndResourceDirectives.scala

  Reason being there are private methods in there and no real way to override.  The re-enabling of symlinks is in the commented out code in def checkIsSafeDescendant

  */
object CustomDirectives {

  private val followSymlinks = true

  /**
   * Same as `getFromBrowseableDirectories` with only one directory.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectory(directory: String)(implicit renderer: DirectoryRenderer, resolver: ContentTypeResolver): Route =
    getFromBrowseableDirectories(directory)

  /**
   * Serves the content of the given directories as a file system browser, i.e. files are sent and directories
   * served as browseable listings.
   *
   * @group fileandresource
   */
  def getFromBrowseableDirectories(directories: String*)(implicit renderer: DirectoryRenderer, resolver: ContentTypeResolver): Route = {
    directories.map(getFromDirectory).reduceLeft(_ ~ _) ~ listDirectoryContents(directories: _*)
  }


  /**
   * Completes GET requests with the content of a file underneath the given directory.
   * If the file cannot be read the Route rejects the request.
   *
   * @group fileandresource
   */
  def getFromDirectory(directoryName: String)(implicit resolver: ContentTypeResolver): Route =
    extractUnmatchedPath { unmatchedPath ⇒
      extractLog { log ⇒
        safeDirectoryChildPath(withTrailingSlash(directoryName), unmatchedPath, log) match {
          case ""       ⇒ reject
          case fileName ⇒ getFromFile(fileName)
        }
      }
    }

  private def safeDirectoryChildPath(basePath: String, path: Uri.Path, log: LoggingAdapter, separator: Char = File.separatorChar): String =
    safeJoinPaths(basePath, path, log, separator) match {
      case ""   ⇒ ""
      case path ⇒ checkIsSafeDescendant(basePath, path, log)
    }

  private def withTrailingSlash(path: String): String = if (path endsWith "/") path else path + '/'

  private def safeJoinPaths(base: String, path: Uri.Path, log: LoggingAdapter, separator: Char = File.separatorChar): String = {
    import java.lang.StringBuilder
    @tailrec def rec(p: Uri.Path, result: StringBuilder = new StringBuilder(base)): String =
      p match {
        case Uri.Path.Empty       ⇒ result.toString
        case Uri.Path.Slash(tail) ⇒ rec(tail, result.append(separator))
        case Uri.Path.Segment(head, tail) ⇒
          if (head.indexOf('/') >= 0 || head.indexOf('\\') >= 0 || head == "..") {
            log.warning("File-system path for base [{}] and Uri.Path [{}] contains suspicious path segment [{}], " +
              "GET access was disallowed", base, path, head)
            ""
          } else rec(tail, result.append(head))
      }
    rec(if (path.startsWithSlash) path.tail else path)
  }

  private def checkIsSafeDescendant(basePath: String, finalPath: String, log: LoggingAdapter): String = {
    val baseFile = new File(basePath)
    val finalFile = new File(finalPath)
    val canonicalFinalPath = finalFile.getCanonicalPath

    if ( !followSymlinks && !canonicalFinalPath.startsWith(baseFile.getCanonicalPath)) {
      log.warning(s"[$finalFile] points to a location that is not part of [$baseFile]. This might be a directory " +
        "traversal attempt.")
      ""
    } else canonicalFinalPath
  }

}