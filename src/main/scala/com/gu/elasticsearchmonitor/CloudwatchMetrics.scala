package com.gu.elasticsearchmonitor

import java.util.Date
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.logging.LogLevel
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.{ Dimension, MetricDatum, PutMetricDataRequest, StandardUnit }

import java.time.Instant
import scala.jdk.CollectionConverters.*

class CloudwatchMetrics(env: Env, cloudWatch: CloudWatchClient) {

  def metricDatum(metricName: String, value: Double, unit: StandardUnit, dimensions: List[(String, String)], now: Instant): MetricDatum = {
    val cloudwatchDimensions = dimensions.map {
      case (dimensionName, dimensionValue) =>
        Dimension
          .builder()
          .name(dimensionName)
          .value(dimensionValue)
          .build()
    }
    MetricDatum.builder()
      .dimensions(cloudwatchDimensions.asJava)
      .metricName(metricName)
      .value(value)
      .unit(unit)
      .timestamp(now)
      .build()
  }

  def buildMetricData(clusterName: String, clusterHealth: ClusterHealth, nodeStats: NodeStats): List[MetricDatum] = {
    val now = Date().toInstant // consistent timestamp across metrics

    def elasticSearchStatusToDouble(status: String): Double = status match {
      case "green" => 0d
      case "yellow" => 1d
      case "red" => 2d
      case _ => 3d
    }
    val defaultDimensions = List("Cluster" -> clusterName)

    val clusterMetrics = List(
      metricDatum("NumberOfNodes", clusterHealth.numberOfNodes.toDouble, StandardUnit.COUNT, defaultDimensions, now),
      metricDatum("NumberOfDataNodes", clusterHealth.numberOfDataNodes.toDouble, StandardUnit.COUNT, defaultDimensions, now),
      metricDatum("Status", elasticSearchStatusToDouble(clusterHealth.status), StandardUnit.NONE, defaultDimensions, now))
    val nodeMetrics = nodeStats.nodes.flatMap { node =>
      val dimensions = defaultDimensions ++ List("InstanceId" -> node.name)
      List(
        metricDatum("AvailableDiskSpace", node.dataAvailable.toDouble, StandardUnit.BYTES, dimensions, now),
        metricDatum("TotalDiskSpace", node.dataTotal.toDouble, StandardUnit.BYTES, dimensions, now),
        metricDatum("JvmHeapUsage", node.jvmHeapUsedPercent.toDouble, StandardUnit.PERCENT, dimensions, now))
    }
    val dataNodes = nodeStats.nodes.filter(_.isDataNode)
    val aggregatedDataNodeMetrics = if (dataNodes.nonEmpty) {
      val minAvailableDiskSpace = dataNodes.minBy(_.dataAvailable).dataAvailable
      val sumAvailableDiskSpace = dataNodes.map(_.dataAvailable).sum
      val sumTotalDiskSpace = dataNodes.map(_.dataTotal).sum
      val maxJvmHeapUsage = dataNodes.maxBy(_.jvmHeapUsedPercent).jvmHeapUsedPercent
      List(
        metricDatum("MinAvailableDiskSpace", minAvailableDiskSpace.toDouble, StandardUnit.BYTES, defaultDimensions, now),
        metricDatum("SumAvailableDiskSpace", sumAvailableDiskSpace.toDouble, StandardUnit.BYTES, defaultDimensions, now),
        metricDatum("SumTotalDiskSpace", sumTotalDiskSpace.toDouble, StandardUnit.BYTES, defaultDimensions, now),
        metricDatum("MaxJvmHeapUsage", maxJvmHeapUsage.toDouble, StandardUnit.PERCENT, defaultDimensions, now))
    } else Nil
    clusterMetrics ++ nodeMetrics ++ aggregatedDataNodeMetrics
  }

  def buildMasterMetricData(clusterName: String, masterInformation: MasterInformation): List[MetricDatum] = {
    val now = Date().toInstant // consistent timestamp across metrics
    val defaultDimensions = List("Cluster" -> clusterName)
    List(
      metricDatum("NumberOfMasterNodes", masterInformation.numberOfMasterInstances, StandardUnit.COUNT, defaultDimensions, now),
      metricDatum("NumberOfRespondingMastersNodes", masterInformation.numberOfRespondingMasters, StandardUnit.COUNT, defaultDimensions, now))
  }

  def sendMetrics(clusterName: String, metrics: List[MetricDatum], logger: LambdaLogger): Unit = {

    logger.log(s"About to send ${metrics.size} metrics", LogLevel.INFO)
    val metricBatches = metrics.grouped(20) // hard limit of 20 items on cloudwatch's side

    metricBatches.foreach { batch =>
      logger.log(s"Sending a batch of ${batch.size} metrics to cloudwatch", LogLevel.INFO)
      val putMetricDataRequest = PutMetricDataRequest
        .builder()
        .namespace(s"${env.stack}/$clusterName")
        .metricData(batch.asJava)
        .build()

      cloudWatch.putMetricData(putMetricDataRequest)
    }
  }

}
