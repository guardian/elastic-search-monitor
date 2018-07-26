package com.gu.elasticsearchmonitor

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.cloudwatch.{ AmazonCloudWatch, AmazonCloudWatchClient }
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.{ Failure, Success, Try }

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

  /*
    Lambda entry point, it is referenced in the cloudformation
   */
  def handler(lambdaInput: LambdaInput, context: Context): Unit = {
    val env = Env()
    logger.info(s"Starting $env")
    process(env)
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

  def process(env: Env): Unit = {
    def fetchAndSendMetrics = for {
      clusterHealth <- ClusterHealth.fetchAndParse(host, httpClient, mapper)
      nodeStats <- NodeStats.fetchAndParse(host, httpClient, mapper)
    } yield {
      cloudwatchMetrics.sendClusterStatus(clusterHealth, nodeStats)
    }

    Try(fetchAndSendMetrics) match {
      case Success(_) => logger.info(s"Successfully finished to send metrics to cloudwatch")
      case Failure(e) => logger.error("Unable to finish processing the metrics", e)
    }
  }
}

object TestIt {
  def main(args: Array[String]): Unit = {
    println(Lambda.process(Env()))
  }
}
