package com.gu.elasticsearchmonitor

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.cloudwatch.{ AmazonCloudWatch, AmazonCloudWatchClient }
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.slf4j.{ Logger, LoggerFactory }

class LambdaInput()

case class Env(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"))
}

object Lambda {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    logger.info(process(env))
  }

  val host = "http://localhost:8000"

  val httpClient = new OkHttpClient()

  val mapper = new ObjectMapper()

  val credentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("deployTools"),
    DefaultAWSCredentialsProviderChain.getInstance)

  val cloudwatch: AmazonCloudWatch = AmazonCloudWatchClient.builder()
    .withCredentials(credentials)
    .withRegion("eu-west-1")
    .build

  val cloudwatchMetrics = new CloudwatchMetrics(Env(), cloudwatch)

  def process(env: Env): String = {
    val result = for {
      clusterHealth <- ClusterHealth.fetchAndParse(host, httpClient, mapper)
      nodeStats <- NodeStats.fetchAndParse(host, httpClient, mapper)
    } yield {
      cloudwatchMetrics.sendClusterStatus(clusterHealth, nodeStats)
    }

    result.toString
  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    println(Lambda.process(Env()))
  }
}
