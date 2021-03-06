name := "scala-rss"

version := "0.1"

scalaVersion := "2.12.4"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.outr" %% "scribe" % "1.4.3",
  "com.outr" %% "scribe-slf4j" % "1.4.3",
  "com.lihaoyi" %% "scalatags" % "0.6.7",
  "com.rometools" % "rome" % "1.8.0",
  "io.monix" %% "monix" % "3.0.0-M1",
  "joda-time" % "joda-time" % "2.9.9",
  "org.json4s" %% "json4s-native" % "3.5.3",
  "org.jsoup" % "jsoup" % "1.10.3",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.scalafx" %% "scalafx" % "8.0.102-R11",
)
