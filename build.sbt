name := """lunatech-wifi"""
organization := "com.lunatech"
version := "1.0.2"

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.8"

resolvers += "Lunatech Artifactory" at "https://artifactory.lunatech.com/artifactory/releases-public"

libraryDependencies ++= Seq(
  guice, ws,
  "com.google.inject" % "guice" % "5.1.0",
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.9.2-akka-2.6.x",
  "org.typelevel" %% "cats-core" % "2.7.0",
  "com.lunatech" %% "play-googleopenconnect" % "2.8.0"
)
