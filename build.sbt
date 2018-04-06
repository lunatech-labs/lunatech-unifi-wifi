name := """lunatech-wifi"""
organization := "com.lunatech"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.4"

resolvers += "Lunatech Artifactory" at "http://artifactory.lunatech.com/artifactory/releases-public"

libraryDependencies ++= Seq(
  guice, ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "com.lunatech" %% "play-googleopenconnect" % "2.2"
)
