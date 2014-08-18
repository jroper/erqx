name := "erqx-engine"

organization := "au.id.jazzy.erqx"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.2"

releaseSettings

publishTo := {
  val localRepo = new File("../jroper.github.io/").getAbsoluteFile
  if (version.value.trim.endsWith("SNAPSHOT")) 
    Some(Resolver.file("snapshots", localRepo / "snapshots"))
  else
    Some(Resolver.file("releases", localRepo / "releases"))
}

// Production dependencies
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-doc" % "1.1.0",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.4.1.201406201815-r",
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
  "radeox" % "radeox" % "1.0-b2" % "test"
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

lazy val customtheme = project.in(file("samples/customtheme"))
  .enablePlugins(PlayScala)
  .dependsOn(root).aggregate(root)

