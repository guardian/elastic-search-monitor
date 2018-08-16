package com.gu.elasticsearchmonitor

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import okhttp3.{ OkHttpClient, Request }
import org.slf4j.{ Logger, LoggerFactory }

import collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

case class NodeStats(
  nodes: List[Node])

object NodeStats {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def fetchAndParse(host: String, httpClient: OkHttpClient, mapper: ObjectMapper): Either[String, NodeStats] = {
    val nodeStatsRequest = new Request.Builder()
      .url(s"$host/_nodes/stats")
      .build()

    val nodeStatsResponse = Try(httpClient.newCall(nodeStatsRequest).execute())

    val result = nodeStatsResponse match {
      case Success(response) if response.code == 200 =>
        val root = mapper.readTree(response.body.string)
        logger.info("Fetched the node stats")
        Right(NodeStats(
          nodes = root.get("nodes").iterator().asScala.toList.map(Node.parse)))
      case Success(response) =>
        Left(s"Unable to fetch the node stats. Http code ${response.code}")
      case Failure(NonFatal(e)) =>
        logger.error("Unable to fetch node stats", e)
        Left(s"Unable to fetch node stats: ${e.getMessage}")
    }

    nodeStatsResponse.foreach(_.close)

    result
  }
}

case class Node(
  name: String,
  dataFree: Long,
  dataTotal: Long,
  jvmHeapUsedPercent: Int,
  isDataNode: Boolean)

object Node {
  def parse(jsonNode: JsonNode): Node = {
    Node(
      name = jsonNode.get("name").asText,
      dataFree = jsonNode.get("fs").get("total").get("free_in_bytes").asLong,
      dataTotal = jsonNode.get("fs").get("total").get("total_in_bytes").asLong,
      jvmHeapUsedPercent = jsonNode.get("jvm").get("mem").get("heap_used_percent").asInt,
      isDataNode = jsonNode.get("roles").elements().asScala.toList.map(_.asText).contains("data"))
  }
}
