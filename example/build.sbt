import com.lihaoyi.workbench.Plugin._

// Turn this project into a Scala.js project by importing these settings
enablePlugins(ScalaJSPlugin)

workbenchSettings

name := "Example"

scalaVersion := "2.11.2"

version := "0.1-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "0.8.0"
)

bootSnippet := "ScalaJSExample().main();"

disableOptimizer := true

spliceBrowsers <<= spliceBrowsers.triggeredBy(fastOptJS in Compile)

