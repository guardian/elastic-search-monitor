name := "elastic-search-monitor"

organization := "com.gu"

description:= "Monitors your elastic search cluster and reports metrics to cloudwatch"

version := "1.0"

scalaVersion := "3.3.7"

val awsSdkVersion = "2.40.3"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.4.0",
  "com.squareup.okhttp3" % "okhttp" % "4.12.0",
  "software.amazon.awssdk" % "cloudwatch" % awsSdkVersion,
  "software.amazon.awssdk" % "ec2" % awsSdkVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.20.1",
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x if x.endsWith("module-info.class") => MergeStrategy.first
  case y =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(y)
}

assemblyJarName := s"${name.value}.jar"
