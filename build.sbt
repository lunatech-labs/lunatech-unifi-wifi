name := """lunatech-wifi"""
organization := "com.lunatech"

version := "1.0.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

resolvers += "Lunatech Artifactory" at "http://artifactory.lunatech.com/artifactory/releases-public"

libraryDependencies ++= Seq(
  guice, ws,
  "com.lunatech" %% "play-googleopenconnect" % "2.4.0"
)
