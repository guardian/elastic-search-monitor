name := "elastic-search-monitor"

organization := "com.gu"

description:= "Monitors your elastic search cluster and reports metrics to cloudwatch"

version := "1.0"

scalaVersion := "3.1.0"

val awsSdkVersion = "1.11.377"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.squareup.okhttp3" % "okhttp" % "3.11.0",
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2",
)

enablePlugins(RiffRaffArtifact)

assembly / assemblyMergeStrategy := {
  case x if x.endsWith("module-info.class") => MergeStrategy.first
  case y =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(y)
}

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cdk/cdk.out/ElasticSearchMonitor-PROD.template.json"), s"cdk.out/ElasticSearchMonitor-PROD.template.json")
