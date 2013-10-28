import sbt._

object Build extends sbt.Build {
  import sbt._

  lazy val runtime = Project("runtime", file("runtime"))
  lazy val plugin = Project("plugin", file("plugin"))


}