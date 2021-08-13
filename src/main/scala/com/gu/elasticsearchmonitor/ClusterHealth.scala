package com.gu.elasticsearchmonitor

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.{ OkHttpClient, Request }
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

case class ClusterHealth(
  clusterName: String,
  status: String,
  numberOfNodes: Int,
  numberOfDataNodes: Int)

object ClusterHealth {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def fetchAndParse(host: String, httpClient: OkHttpClient, mapper: ObjectMapper): Either[String, ClusterHealth] = {
    val clusterHealthRequest = new Request.Builder()
      .url(s"$host/_cluster/health")
      .build()

    val shardAllocationRequest = new Request.Builder()
      .url(s"$host/_cluster/allocation/explain")
      .build()

    val nodeInfoRequest = new Request.Builder()
      .url(s"$host/_nodes")
      .build()

    val clusterHealthResponse = Try(httpClient.newCall(clusterHealthRequest).execute())

    val result = clusterHealthResponse match {
      case Success(response) if response.code == 200 =>
        val root = mapper.readTree(response.body.string)
        logger.info("Fetched the cluster health")
        val clusterStatus = root.get("status").asText
        if (clusterStatus != "green") {
          val requestAttempts = for {
            shardRequest <- Try(httpClient.newCall(shardAllocationRequest).execute())
            nodeRequest <- Try(httpClient.newCall(nodeInfoRequest).execute())
          } yield {
            logger.warn(s"Cluster is in $clusterStatus status. Shard allocation info: ${shardRequest.body.string} | Node info: ${nodeRequest.body.string}")
          }
          requestAttempts.recover {
            case exception =>
              logger.warn(s"Failed to obtain debug information about cluster status due to $exception")
          }
        }
        Right(ClusterHealth(
          clusterName = root.get("cluster_name").asText,
          status = clusterStatus,
          numberOfNodes = root.get("number_of_nodes").asInt,
          numberOfDataNodes = root.get("number_of_data_nodes").asInt))

      case Success(response) =>
        Left(s"Unable to fetch the cluster health status. Http code ${response.code}")

      case Failure(NonFatal(e)) =>
        logger.error("Unable to fetch cluster health", e)
        Left(s"Unable to fetch cluster health: ${e.getMessage}")
    }

    clusterHealthResponse.foreach(_.close)

    result
  }
}
