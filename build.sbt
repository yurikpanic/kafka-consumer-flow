name := "kafka-consumer-flow"
organization := "com.elsyton"
version := "1.0.0"

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.11.11", "2.12.2")

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-encoding",
  "utf8",
  "-feature",
  "-target:jvm-1.8",
  "-Xfuture",
  "-Yno-adapted-args",
  "-Ywarn-dead-code")

libraryDependencies := {
  val akkaV          = "2.5.2"
  val scalaTestV     = "3.0.1"
  val dockerTestkitV = "0.9.1"
  val kafkaVersion   = "0.10.2.1"

  Seq(
    "org.apache.kafka"  % "kafka-clients"                % kafkaVersion,
    "com.typesafe.akka" %% "akka-actor"                  % akkaV,
    "com.typesafe.akka" %% "akka-stream"                 % akkaV,
    "com.typesafe.akka" %% "akka-slf4j"                  % akkaV,
    "ch.qos.logback"    % "logback-classic"              % "1.2.3",
    "com.typesafe.akka" %% "akka-testkit"                % akkaV % Test,
    "org.scalatest"     %% "scalatest"                   % scalaTestV % Test,
    "com.whisk"         %% "docker-testkit-scalatest"    % dockerTestkitV % Test,
    "com.whisk"         %% "docker-testkit-impl-spotify" % dockerTestkitV % Test
  )
}

test in assembly := {}
