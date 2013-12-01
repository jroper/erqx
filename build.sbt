name := "erqx-engine"

organization := "au.id.jazzy.erqx"

version := "1.0.0-SNAPSHOT"

play.Project.playScalaSettings

playPlugin := true

// Production dependencies
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-doc" % "1.0.5",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.0.0.201306101825-r",
  "org.yaml" % "snakeyaml" % "1.12"
)

// Web dependencies
libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.2.0",
  "org.webjars" % "bootstrap" % "3.0.2",
  "org.webjars" % "prettify" % "4-Mar-2013",
  "org.webjars" % "retinajs" % "0.0.2"
)

// Test dependencies
libraryDependencies ++= Seq(
  "radeox" % "radeox" % "1.0-b2" % "test"
)

// Version file
sourceGenerators in Compile <+= sourceManaged in Compile map { dir =>
  val hash = ("git rev-parse HEAD" !!).trim
  val file = dir / "au" / "id" / "jazzy" / "erqx" / "engine" / "ErqxBuild.scala"
  IO.write(file,
    """ |package au.id.jazzy.erqx.engine
        |
        |object ErqxBuild {
        |  val hash = "%s"
        |}
      """.stripMargin.format(hash))
  Seq(file)
}

lazy val root = project in file(".")

lazy val minimal = project.in(file("samples/minimal"))
  .dependsOn(root).aggregate(root)

lazy val customtheme = project.in(file("samples/customtheme"))
  .dependsOn(root).aggregate(root)

