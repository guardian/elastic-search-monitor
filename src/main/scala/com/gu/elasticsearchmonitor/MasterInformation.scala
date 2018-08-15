package com.gu.elasticsearchmonitor

case class MasterInformation(
  numberOfMasterInstances: Int,
  numberOfRespondingMasters: Int,
  aRandomMasterUrl: Option[String])
