package com.gu.elasticsearchmonitor

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import okhttp3.{ OkHttpClient, Request }

import collection.JavaConverters._

case class NodeStats(
  nodes: List[Node])

object NodeStats {

  def fetchAndParse(host: String, httpClient: OkHttpClient, mapper: ObjectMapper): Either[String, NodeStats] = {
    val nodeStatsRequest = new Request.Builder()
      .url(s"$host/_nodes/stats")
      .build()

    val nodeStatsResponse = httpClient.newCall(nodeStatsRequest).execute()
    val body = nodeStatsResponse.body().string()
    nodeStatsResponse.close()
    if (nodeStatsResponse.code() != 200) {
      Left(s"Unable to fetch the node stats. Http code ${nodeStatsResponse.code()}")
    } else {
      val root = mapper.readTree(body)
      Right(NodeStats(
        nodes = root.get("nodes").iterator().asScala.toList.map(Node.parse)))
    }
  }
}

case class Node(
  name: String,
  dataFree: Long,
  dataTotal: Long,
  jvmHeapUsedPercent: Int)

object Node {
  def parse(jsonNode: JsonNode): Node = {
    Node(
      name = jsonNode.get("name").asText,
      dataFree = jsonNode.get("fs").get("total").get("free_in_bytes").asLong,
      dataTotal = jsonNode.get("fs").get("total").get("total_in_bytes").asLong,
      jvmHeapUsedPercent = jsonNode.get("jvm").get("mem").get("heap_used_percent").asInt)
  }
}
