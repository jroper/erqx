import scala.sys.process._

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

name := "erqx-engine"
organization := "au.id.jazzy.erqx"

LessKeys.compress := true

bintrayRepository := "maven"
bintrayPackage := "erqx"
releaseCrossBuild := true
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.13.8"
crossScalaVersions := Seq("2.12.16", "2.13.8")

// Production dependencies
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-doc" % "2.1.0",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.1.3.201810200350-r",
  "org.yaml" % "snakeyaml" % "1.23"
)

// Web dependencies
libraryDependencies ++= Seq(
  "org.webjars" % "bootstrap" % "3.2.0",
  "org.webjars" % "prettify" % "4-Mar-2013",
  "org.webjars" % "retinajs" % "0.0.2"
)

// Test dependencies
libraryDependencies ++= Seq(
  "radeox" % "radeox" % "1.0-b2" % "test",
  specs2
)

// Version file
Compile / sourceGenerators += Def.task {
  val dir = (Compile / sourceManaged).value
  val hash = "git rev-parse HEAD".!!.trim
  val file = dir / "au" / "id" / "jazzy" / "erqx" / "engine" / "ErqxBuild.scala"
  if (!file.exists || !IO.read(file).contains(hash)) {
    IO.write(file,
      """ |package au.id.jazzy.erqx.engine
        |
        |object ErqxBuild {
        |  val hash = "%s"
        |  val version = "%s"
        |}
      """.stripMargin.format(hash, version.value))
  }
  Seq(file)
}.taskValue

lazy val minimal = project.in(file("samples/minimal"))
  .enablePlugins(PlayScala)
  .dependsOn(root).aggregate(root)

// Concatinate erqx theme assets
Concat.groups := Seq(
  "erqx-jazzy-theme.css" -> group(Seq("lib/bootstrap/css/bootstrap.min.css", "main.min.css", "lib/prettify/prettify.css")),
  "erqx-jazzy-theme.js" -> group(Seq("lib/jquery/jquery.min.js", "lib/prettify/prettify.js", "lib/prettify/lang-scala.js", "lib/retinajs/retina.js"))
)

Assets / pipelineStages := Seq(concat)
