package com.gu.elasticsearchmonitor

import java.util.Date

import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.{ Dimension, MetricDatum, PutMetricDataRequest, StandardUnit }
import org.slf4j.{ Logger, LoggerFactory }

import collection.JavaConverters._

class CloudwatchMetrics(env: Env, cloudWatch: AmazonCloudWatch) {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def metricDatum(metricName: String, value: Double, unit: StandardUnit, dimensions: List[(String, String)], now: Date): MetricDatum = {
    val metricDatum = new MetricDatum

    val cloudwatchDimensions = dimensions.map {
      case (dimensionName, dimensionValue) =>
        val cloudwatchDimension = new Dimension
        cloudwatchDimension.setName(dimensionName)
        cloudwatchDimension.setValue(dimensionValue)
        cloudwatchDimension
    }
    metricDatum.setDimensions(cloudwatchDimensions.asJava)
    metricDatum.setMetricName(metricName)
    metricDatum.setValue(value)
    metricDatum.setUnit(unit)
    metricDatum.setTimestamp(now)
    metricDatum
  }

  def buildMetricData(clusterName: String, clusterHealth: ClusterHealth, nodeStats: NodeStats): List[MetricDatum] = {
    val now = new Date() // consistent timestamp across metrics

    def elasticSearchStatusToDouble(status: String): Double = status match {
      case "green" => 0d
      case "yellow" => 1d
      case "red" => 2d
      case _ => 3d
    }
    val defaultDimensions = List("Cluster" -> clusterName)

    val clusterMetrics = List(
      metricDatum("NumberOfNodes", clusterHealth.numberOfNodes.toDouble, StandardUnit.Count, defaultDimensions, now),
      metricDatum("NumberOfDataNodes", clusterHealth.numberOfDataNodes.toDouble, StandardUnit.Count, defaultDimensions, now),
      metricDatum("Status", elasticSearchStatusToDouble(clusterHealth.status), StandardUnit.None, defaultDimensions, now))
    val nodeMetrics = nodeStats.nodes.flatMap { node =>
      val dimensions = defaultDimensions ++ List("InstanceId" -> node.name)
      List(
        metricDatum("FreeDiskSpace", node.dataFree.toDouble, StandardUnit.Bytes, dimensions, now),
        metricDatum("TotalDiskSpace", node.dataTotal.toDouble, StandardUnit.Bytes, dimensions, now),
        metricDatum("JvmHeapUsage", node.jvmHeapUsedPercent.toDouble, StandardUnit.Percent, dimensions, now))
    }
    val dataNodes = nodeStats.nodes.filter(_.isDataNode)
    val aggregatedDataNodeMetrics = if (dataNodes.nonEmpty) {
      val minFreeDiskSpace = dataNodes.minBy(_.dataFree).dataFree
      val sumFreeDiskSpace = dataNodes.map(_.dataFree).sum
      val sumTotalDiskSpace = dataNodes.map(_.dataTotal).sum
      val maxJvmHeapUsage = dataNodes.maxBy(_.jvmHeapUsedPercent).jvmHeapUsedPercent
      List(
        metricDatum("MinFreeDiskSpace", minFreeDiskSpace.toDouble, StandardUnit.Bytes, defaultDimensions, now),
        metricDatum("SumFreeDiskSpace", sumFreeDiskSpace.toDouble, StandardUnit.Bytes, defaultDimensions, now),
        metricDatum("SumTotalDiskSpace", sumTotalDiskSpace.toDouble, StandardUnit.Bytes, defaultDimensions, now),
        metricDatum("MaxJvmHeapUsage", maxJvmHeapUsage.toDouble, StandardUnit.Bytes, defaultDimensions, now))
    } else Nil
    clusterMetrics ++ nodeMetrics ++ aggregatedDataNodeMetrics
  }

  def buildMasterMetricData(clusterName: String, masterInformation: MasterInformation): List[MetricDatum] = {
    val now = new Date() // consistent timestamp across metrics
    val defaultDimensions = List("Cluster" -> clusterName)
    List(
      metricDatum("NumberOfMasterNodes", masterInformation.numberOfMasterInstances, StandardUnit.Count, defaultDimensions, now),
      metricDatum("NumberOfRespondingMastersNodes", masterInformation.numberOfRespondingMasters, StandardUnit.Count, defaultDimensions, now))
  }

  def sendMetrics(clusterName: String, metrics: List[MetricDatum]): Unit = {

    logger.info(s"About to send ${metrics.size} metrics")
    val metricBatches = metrics.grouped(20) // hard limit of 20 items on cloudwatch's side

    metricBatches.foreach { batch =>
      logger.info(s"Sending a batch of ${batch.size} metrics to cloudwatch")
      val putMetricDataRequest = new PutMetricDataRequest()
      putMetricDataRequest.setNamespace(s"${env.stack}/$clusterName")
      putMetricDataRequest.setMetricData(batch.asJava)

      cloudWatch.putMetricData(putMetricDataRequest)
    }
  }

}
