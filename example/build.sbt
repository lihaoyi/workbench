enablePlugins(ScalaJSPlugin)
enablePlugins(WorkbenchPlugin)

name := "Example"

scalaVersion := "2.12.0"

version := "0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1"
)

// (experimental feature)
spliceBrowsers <<= spliceBrowsers.triggeredBy(fastOptJS in Compile)
