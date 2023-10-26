name := """lunatech-wifi"""
organization := "com.lunatech"
version := "1.0.2"

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  guice, ws,
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.9.3-akka-2.6.x",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "com.lunatech" %% "play-googleopenconnect" % "2.9.7"
)
