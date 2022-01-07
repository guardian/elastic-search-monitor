package com.gu.elasticsearchmonitor

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{ DescribeInstancesRequest, Filter, Instance }
import okhttp3.{ OkHttpClient, Request }
import org.slf4j.{ Logger, LoggerFactory }

import scala.jdk.CollectionConverters.*
import scala.annotation.tailrec
import scala.util.{ Failure, Random, Success, Try }

class MasterDetector(amazonEC2: AmazonEC2, httpClient: OkHttpClient) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def detectMasters(env: Env): Either[String, MasterInformation] = {
    @tailrec
    def queryInstances(instancesFromPreviousRequest: List[Instance], nextToken: Option[String]): List[Instance] = {
      val filters = List(
        new Filter("tag:Stage", List(env.stage).asJava),
        new Filter("tag:Stack", List(env.tagQueryStack).asJava),
        new Filter("tag:App", List(env.tagQueryApp).asJava))
      val dir = new DescribeInstancesRequest().withFilters(filters: _*).withNextToken(nextToken.orNull)
      val result = amazonEC2.describeInstances(dir)
      val instances = instancesFromPreviousRequest ++ result.getReservations.asScala.flatMap(_.getInstances.asScala)
      if (result.getNextToken == null) {
        instances
      } else {
        queryInstances(instances, Option(result.getNextToken))
      }
    }

    def masterRespondsToHealthCheck(instanceName: String): Boolean = {
      val clusterHealthRequest = new Request.Builder()
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
        logger.info(s"Identified these instances as potential candidates: $instanceNames")
        val aliveInstances = instanceNames.filter(masterRespondsToHealthCheck)

        val masterInfo = MasterInformation(
          numberOfMasterInstances = instanceNames.size,
          numberOfRespondingMasters = aliveInstances.size,
          aRandomMasterUrl = Random.shuffle(aliveInstances).headOption) //any master should do
        logger.info(s"Found the following master information: $masterInfo")
        Right(masterInfo)
      case Failure(e) =>
        logger.error("Unable to fetch master instances", e)
        Left(s"Unable to fetch master instances: ${e.getMessage}")
    }

  }
}
