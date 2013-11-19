name := "jazzy"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-doc" % "1.0.5",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.0.0.201306101825-r",
  "org.webjars" %% "webjars-play" % "2.2.0",
  "org.webjars" % "bootstrap" % "3.0.2",
  "org.webjars" % "prettify" % "4-Mar-2013",
  "org.webjars" % "jquery" % "2.0.3-1",
  "radeox" % "radeox" % "1.0-b2" % "test"
)

play.Project.playScalaSettings
