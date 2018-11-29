name := "Spark1"

version := "0.1"

scalaVersion := "2.11.12"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature"
)

val sparkVersion = "2.1.0"

libraryDependencies ++= Seq(
  "com.github.catalystcode" %% "streaming-rss-html" % "1.0.2",
  "org.apache.spark" %% "spark-core" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0",
  "org.apache.spark" %% "spark-streaming" % "2.2.0",
  "org.apache.spark" %% "spark-mllib" % "2.2.0"
).map(_ % "compile")

// TODO: can be deleted
libraryDependencies ++= Seq(
  "com.rometools" % "rome" % "1.8.0",
  "org.jsoup" % "jsoup" % "1.10.3",
  "log4j" % "log4j" % "1.2.17"
)

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-core" % "2.8.47",
  "org.mockito" % "mockito-inline" % "2.8.47",
  "org.scalatest" %% "scalatest" % "2.2.1"
).map(_ % "test")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
