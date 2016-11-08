enablePlugins(ScalaJSPlugin)

// dynamic page reloading
enablePlugins(WorkbenchPlugin)

// (experimental feature) in-place code update with state preservation
// enablePlugins(WorkbenchSplicePlugin) // disable WorkbenchPlugin when activating

name := "Example"

scalaVersion := "2.12.0"

version := "0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.9.1"
)
