import sbt.Keys._

val defaultSettings = Seq(
  unmanagedSourceDirectories in Compile <+= baseDirectory(_ /  "shared" / "main" / "scala"),
  unmanagedSourceDirectories in Test <+= baseDirectory(_ / "shared" / "test" / "scala")
)

lazy val plugin = project.in(file("plugin")).settings(defaultSettings:_*).settings(
  name := "workbench",
  version := "0.1.5",
  organization := "com.lihaoyi",
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
  (resources in Compile) := {(resources in Compile).value ++ (baseDirectory.value * "*.js").get},
  libraryDependencies ++= Seq(
    "io.spray" % "spray-can" % "1.3.1",
    "io.spray" % "spray-routing" % "1.3.1",
    "com.typesafe.akka" %%  "akka-actor" % "2.3.0",
    "com.lihaoyi" %% "upickle" % "0.2.1"
  )
)

lazy val client = project.in(file("client"))
                         .settings(defaultSettings ++ scalaJSSettings:_*)
                         .settings(
  libraryDependencies ++= Seq(
    "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.6",
    "org.scala-lang.modules" %% "scala-async" % "0.9.2",
    "com.lihaoyi" %%% "upickle" % "0.2.1"
  )
)