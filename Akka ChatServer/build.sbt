ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "Chat Server Akka"
  )
val akkaVersion = "2.6.20"
val scalaTestVersion = "3.2.9"
val logbackVersion = "1.2.10"
val slf4j_version = "1.7.5"
val log4j_api_version = "2.17.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "org.slf4j" % "slf4j-api" % slf4j_version,
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "org.apache.logging.log4j" % "log4j-api" % log4j_api_version,
  "org.scalatest" %% "scalatest" % scalaTestVersion,
  //  "ch.qos.logback" % "logback-classic" % logbackVersion
)