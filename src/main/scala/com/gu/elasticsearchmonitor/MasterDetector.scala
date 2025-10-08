package com.gu.elasticsearchmonitor

import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.logging.LogLevel
import okhttp3.{ OkHttpClient, Request }
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.{ DescribeInstancesRequest, Filter, Instance }

import scala.jdk.CollectionConverters.*
import scala.util.{ Failure, Random, Success, Try }

class MasterDetector(amazonEC2: Ec2Client, httpClient: OkHttpClient) {

  def detectMasters(env: Env, logger: LambdaLogger): Either[String, MasterInformation] = {
    def queryInstances(instancesFromPreviousRequest: List[Instance], nextToken: Option[String]): List[Instance] = {
      val filters = List(
        Filter.builder().name("tag:Stage").values(List(env.stage).asJava).build(),
        Filter.builder().name("tag:Stack").values(List(env.tagQueryStack).asJava).build(),
        Filter.builder().name("tag:App").values(List(env.tagQueryApp).asJava).build())
      val instancesRequest = DescribeInstancesRequest.builder()
        .filters(filters.asJava)
        .build()
      val paginator = amazonEC2.describeInstancesPaginator(instancesRequest)
      paginator.asScala.toList.flatMap(_.reservations.asScala.toList.flatMap(_.instances.asScala))
    }

    def masterRespondsToHealthCheck(instanceName: String): Boolean = {
      val clusterHealthRequest = Request.Builder()
        .url(s"$instanceName/_cluster/health")
        .build()

      val clusterHealthResponse = Try(httpClient.newCall(clusterHealthRequest).execute())
      val result = clusterHealthResponse match {
        case Success(response) if response.code == 200 => true
        case _ => false
      }
      clusterHealthResponse.foreach(_.close)
      result
    }

    Try(queryInstances(Nil, None)) match {
      case Success(instances) =>
        val instanceNames = instances.map(instance => s"http://${instance.privateIpAddress}:9200")
        logger.log(s"Identified these instances as potential candidates: $instanceNames", LogLevel.INFO)
        val aliveInstances = instanceNames.filter(masterRespondsToHealthCheck)

        val masterInfo = MasterInformation(
          numberOfMasterInstances = instanceNames.size,
          numberOfRespondingMasters = aliveInstances.size,
          aRandomMasterUrl = Random.shuffle(aliveInstances).headOption) //any master should do
        logger.log(s"Found the following master information: $masterInfo", LogLevel.INFO)
        Right(masterInfo)
      case Failure(e) =>
        logger.log(s"Unable to fetch master instances: $e", LogLevel.ERROR)
        Left(s"Unable to fetch master instances: ${e.getMessage}")
    }

  }
}
