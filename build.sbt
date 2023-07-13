name := "elastic-search-monitor"

organization := "com.gu"

description:= "Monitors your elastic search cluster and reports metrics to cloudwatch"

version := "1.0"

scalaVersion := "3.1.3"

val awsSdkVersion = "1.12.505"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
  "org.slf4j" % "slf4j-simple" % "1.7.36",
  "com.squareup.okhttp3" % "okhttp" % "3.14.9",
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.3",
)

assembly / assemblyMergeStrategy := {
  case x if x.endsWith("module-info.class") => MergeStrategy.first
  case y =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(y)
}

assemblyJarName := s"${name.value}.jar"
