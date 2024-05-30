name := "elastic-search-monitor"

organization := "com.gu"

description:= "Monitors your elastic search cluster and reports metrics to cloudwatch"

version := "1.0"

scalaVersion := "3.3.3"

val awsSdkVersion = "1.12.732"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
  "com.squareup.okhttp3" % "okhttp" % "4.12.0",
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.1",
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x if x.endsWith("module-info.class") => MergeStrategy.first
  case y =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(y)
}

assemblyJarName := s"${name.value}.jar"
