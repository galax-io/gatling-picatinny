package org.galaxio.gatling.influxdb

@deprecated("InfluxDB integration is deprecated and will be removed in a future release.", since = "2025-08")
sealed trait Status

@deprecated("InfluxDB integration is deprecated and will be removed in a future release.", since = "2025-08")
case object Start extends Status

@deprecated("InfluxDB integration is deprecated and will be removed in a future release.", since = "2025-08")
case object Stop extends Status
