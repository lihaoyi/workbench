import sbt.Keys._

name := "workbench"

version := "0.1"

organization := "com.lihaoyi"

sbtPlugin := true

// Sonatype
publishArtifact in Test := false

publishTo <<= version { (v: String) =>
  Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
}

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
  )

(resources in Compile) := {(resources in Compile).value ++ (baseDirectory.value * "*.ts").get}

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "typesafe" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "io.spray" % "spray-can" % "1.2.0",
  "io.spray" % "spray-routing" % "1.2.0",
  "com.typesafe.akka"   %%  "akka-actor"    % "2.2.3",
  "com.typesafe.play" %% "play-json" % "2.2.0-RC1"
)
