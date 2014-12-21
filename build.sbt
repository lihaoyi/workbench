import sbt.Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import ScalaJSKeys._

val defaultSettings = Seq(
  unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
  unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")
)

lazy val root = project.in(file(".")).settings(defaultSettings:_*).settings(
  name := "workbench",
  version := "0.2.3",
  organization := "com.lihaoyi",
  scalaVersion := "2.10.4",
  sbtPlugin := true,
  publishArtifact in Test := false,
  publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
  pomExtra := (
    <url>https://github.com/lihaoyi/workbench</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/lihaoyi/workbench.git</url>
        <connection>scm:git://github.com/lihaoyi/workbench.git</connection>
      </scm>
      <developers>
        <developer>
          <id>lihaoyi</id>
          <name>Li Haoyi</name>
          <url>https://github.com/lihaoyi</url>
        </developer>
      </developers>
  ),
  (resources in Compile) += {
    (fullOptJS in (client, Compile)).value
    (artifactPath in (client, Compile, fullOptJS)).value
  },
  resolvers += Resolver.url("scala-js-releases",
    url("http://dl.bintray.com/content/scala-js/scala-js-releases"))(
      Resolver.ivyStylePatterns),
  addSbtPlugin("org.scala-lang.modules.scalajs" % "scalajs-sbt-plugin" % "0.5.4"),
  libraryDependencies ++= Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value,
    "org.scala-lang.modules.scalajs" % s"scalajs-compiler_${scalaVersion.value}" % "0.5.4",
    "org.scala-lang.modules.scalajs" %% "scalajs-tools" % "0.5.4",
    "io.spray" % "spray-can" % "1.3.1",
    "io.spray" % "spray-routing" % "1.3.1",
    "com.typesafe.akka" %% "akka-actor" % "2.3.0",
    "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
    "com.lihaoyi" %% "autowire" % "0.2.3",
    "com.lihaoyi" %% "upickle" % "0.2.5"
  ),
  resolvers += "bintray/non" at "http://dl.bintray.com/non/maven"
)

lazy val client = project.in(file("client"))
                         .settings(defaultSettings ++ scalaJSSettings:_*)
                         .settings(
  libraryDependencies ++= Seq(
    "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6",
    "com.lihaoyi" %%% "autowire" % "0.2.3",
    "com.lihaoyi" %%% "upickle" % "0.2.5"
  ),
  emitSourceMaps := false
)
