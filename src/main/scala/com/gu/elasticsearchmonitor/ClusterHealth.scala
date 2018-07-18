package com.gu.elasticsearchmonitor

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.{ OkHttpClient, Request }

case class ClusterHealth(
  clusterName: String,
  status: String,
  numberOfNodes: Int,
  numberOfDataNodes: Int)

object ClusterHealth {

  def fetchAndParse(host: String, httpClient: OkHttpClient, mapper: ObjectMapper): Either[String, ClusterHealth] = {
    val clusterHealthRequest = new Request.Builder()
      .url(s"$host/_cluster/health")
      .build()

    val clusterHealthResponse = httpClient.newCall(clusterHealthRequest).execute()
    val body = clusterHealthResponse.body().string()
    clusterHealthResponse.close()
    if (clusterHealthResponse.code() != 200) {
      Left(s"Unable to fetch the cluster health status. Http code ${clusterHealthResponse.code()}")
    } else {
      val root = mapper.readTree(body)
      Right(ClusterHealth(
        clusterName = root.get("cluster_name").asText,
        status = root.get("status").asText,
        numberOfNodes = root.get("number_of_nodes").asInt,
        numberOfDataNodes = root.get("number_of_data_nodes").asInt))
    }
  }
}