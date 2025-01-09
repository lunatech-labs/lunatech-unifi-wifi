name := """lunatech-wifi"""
organization := "com.lunatech"
version := "1.0.2"

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.15"

libraryDependencies ++= Seq(
  guice, ws,
  "io.github.samueleresca" %% "pekko-quartz-scheduler" % "1.2.2-pekko-1.0.x",
  "org.typelevel" %% "cats-core" % "2.12.0",
  "com.lunatech" %% "play-googleopenconnect" % "3.0.3"
)
