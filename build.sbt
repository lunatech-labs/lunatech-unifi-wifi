name := """lunatech-wifi"""
organization := "com.lunatech"

version := "1.0.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

resolvers += "Lunatech Artifactory" at "http://artifactory.lunatech.com/artifactory/releases-public"

libraryDependencies ++= Seq(
  guice, ws,
  "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
  "org.typelevel" %% "cats-core" % "1.6.0",
  "com.lunatech" %% "play-googleopenconnect" % "2.4.0"
)
