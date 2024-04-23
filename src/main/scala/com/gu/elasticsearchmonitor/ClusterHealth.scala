package com.gu.elasticsearchmonitor

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.logging.LogLevel
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.{ OkHttpClient, Request }

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

case class ClusterHealth(
  clusterName: String,
  status: String,
  numberOfNodes: Int,
  numberOfDataNodes: Int)

object ClusterHealth {

  def fetchAndParse(host: String, httpClient: OkHttpClient, mapper: ObjectMapper, logger: LambdaLogger): Either[String, ClusterHealth] = {
    val clusterHealthRequest = Request.Builder()
      .url(s"$host/_cluster/health")
      .build()

    val shardAllocationRequest = Request.Builder()
      .url(s"$host/_cluster/allocation/explain")
      .build()

    val nodeInfoRequest = Request.Builder()
      .url(s"$host/_nodes")
      .build()

    val clusterHealthResponse = Try(httpClient.newCall(clusterHealthRequest).execute())

    val result = clusterHealthResponse match {
      case Success(response) if response.code == 200 =>
        val root = mapper.readTree(response.body.string)
        logger.log("Fetched the cluster health", LogLevel.INFO)
        val clusterStatus = root.get("status").asText
        if (clusterStatus != "green") {
          val requestAttempts = for {
            shardRequest <- Try(httpClient.newCall(shardAllocationRequest).execute())
            nodeRequest <- Try(httpClient.newCall(nodeInfoRequest).execute())
          } yield {
            logger.log(s"Cluster is in $clusterStatus status. Shard allocation info: ${shardRequest.body.string} | Node info: ${nodeRequest.body.string}", LogLevel.WARN)
          }
          requestAttempts.recover {
            case exception =>
              logger.log(s"Failed to obtain debug information about cluster status due to $exception", LogLevel.WARN)
          }
        }
        Right(ClusterHealth(
          clusterName = root.get("cluster_name").asText,
          status = clusterStatus,
          numberOfNodes = root.get("number_of_nodes").asInt,
          numberOfDataNodes = root.get("number_of_data_nodes").asInt))

      case Success(response) =>
        Left(s"Unable to fetch the cluster health status. Http code ${response.code}")

      case Failure(e) =>
        logger.log(s"Unable to fetch cluster health: $e", LogLevel.ERROR)
        Left(s"Unable to fetch cluster health: ${e.getMessage}")
    }

    clusterHealthResponse.foreach(_.close)

    result
  }
}
