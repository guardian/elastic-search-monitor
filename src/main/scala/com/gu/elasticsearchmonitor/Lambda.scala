package com.gu.elasticsearchmonitor

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.cloudwatch.{ AmazonCloudWatch, AmazonCloudWatchClient }
import com.amazonaws.services.ec2.{ AmazonEC2, AmazonEC2Client }
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.{ Failure, Success, Try }

class LambdaInput()

case class Env(
  app: String,
  stack: String,
  stage: String,
  tagQueryApp: String,
  tagQueryStack: String,
  clusterName: String)

object Env {
  def apply(): Env = Env(
    Option(System.getenv("APP")).getOrElse("DEV"),
    Option(System.getenv("STACK")).getOrElse("DEV"),
    Option(System.getenv("STAGE")).getOrElse("DEV"),
    Option(System.getenv("TAG_QUERY_APP")).getOrElse("DEV"),
    Option(System.getenv("TAG_QUERY_STACK")).getOrElse("DEV"),
    Option(System.getenv("CLUSTER_NAME")).getOrElse("DEV"))
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

  val httpClient = OkHttpClient()

  val mapper = ObjectMapper()

  val credentials = AWSCredentialsProviderChain(
    ProfileCredentialsProvider("deployTools"),
    DefaultAWSCredentialsProviderChain.getInstance)

  val cloudwatch: AmazonCloudWatch = AmazonCloudWatchClient.builder()
    .withCredentials(credentials)
    .withRegion("eu-west-1")
    .build

  val ec2: AmazonEC2 = AmazonEC2Client.builder()
    .withCredentials(credentials)
    .withRegion("eu-west-1")
    .build

  val cloudwatchMetrics = CloudwatchMetrics(Env(), cloudwatch)

  val masterDetector = MasterDetector(ec2, httpClient)

  def process(env: Env): Unit = {
    def resolveMasterHostName(masterInfo: MasterInformation): Either[String, String] =
      if (env.stage == "DEV") {
        logger.info(s"would have resolved with master node ${masterInfo.aRandomMasterUrl}, but forcing back to localhost as the lambda is running locally")
        Right("http://localhost:8000")
      } else Either.cond(masterInfo.aRandomMasterUrl.isDefined, masterInfo.aRandomMasterUrl.get, "No Master node!")

    def fetchAndSendMetrics = for {
      masterInfo <- masterDetector.detectMasters(env)
    } yield {
      val masterMetrics = cloudwatchMetrics.buildMasterMetricData(env.clusterName, masterInfo)
      val clusterMetrics = for {
        masterHostName <- resolveMasterHostName(masterInfo)
        clusterHealth <- ClusterHealth.fetchAndParse(masterHostName, httpClient, mapper)
        nodeStats <- NodeStats.fetchAndParse(masterHostName, httpClient, mapper)
      } yield {
        cloudwatchMetrics.buildMetricData(env.clusterName, clusterHealth, nodeStats)
      }
      clusterMetrics.left.foreach(error => logger.error(s"Couldn't fetch cluster health: $error"))

      val allMetrics = masterMetrics ++ clusterMetrics.getOrElse(Nil)
      cloudwatchMetrics.sendMetrics(env.clusterName, allMetrics)
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
