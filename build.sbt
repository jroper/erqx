lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

name := "erqx-engine"
organization := "au.id.jazzy.erqx"

LessKeys.compress := true

bintrayRepository := "maven"
bintrayPackage := "erqx"
releasePublishArtifactsAction := PgpKeys.publishSigned.value
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

scalaVersion := "2.11.8"

// Production dependencies
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-doc" % "1.2.1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.7.0.201502260915-r",
  "org.yaml" % "snakeyaml" % "1.12"
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
sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
  val hash = ("git rev-parse HEAD" !!).trim
  val file = dir / "au" / "id" / "jazzy" / "erqx" / "engine" / "ErqxBuild.scala"
  if (!file.exists || !IO.read(file).contains(hash)) {
    IO.write(file,
      """ |package au.id.jazzy.erqx.engine
          |
          |object ErqxBuild {
          |  val hash = "%s"
          |}
        """.stripMargin.format(hash))
  }
  Seq(file)
}

lazy val minimal = project.in(file("samples/minimal"))
  .enablePlugins(PlayScala)
  .dependsOn(root).aggregate(root)
