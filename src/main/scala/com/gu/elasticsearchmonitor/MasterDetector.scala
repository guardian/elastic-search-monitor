package com.gu.elasticsearchmonitor

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Instance }
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.logging.LogLevel
import okhttp3.{ OkHttpClient, Request }

import scala.jdk.CollectionConverters.*
import scala.annotation.tailrec
import scala.util.{ Failure, Random, Success, Try }

class MasterDetector(amazonEC2: AmazonEC2, httpClient: OkHttpClient) {

  def detectMasters(env: Env, logger: LambdaLogger): Either[String, MasterInformation] = {
    @tailrec
    def queryInstances(instancesFromPreviousRequest: List[Instance], nextToken: Option[String]): List[Instance] = {
      val filters = List(
        Filter("tag:Stage", List(env.stage).asJava),
        Filter("tag:Stack", List(env.tagQueryStack).asJava),
        Filter("tag:App", List(env.tagQueryApp).asJava))
      val dir = DescribeInstancesRequest().withFilters(filters: _*).withNextToken(nextToken.orNull)
      val result = amazonEC2.describeInstances(dir)
      val instances = instancesFromPreviousRequest ++ result.getReservations.asScala.flatMap(_.getInstances.asScala)
      if (result.getNextToken == null) {
        instances
      } else {
        queryInstances(instances, Option(result.getNextToken))
      }
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
        val instanceNames = instances.map(instance => s"http://${instance.getPrivateIpAddress}:9200")
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
