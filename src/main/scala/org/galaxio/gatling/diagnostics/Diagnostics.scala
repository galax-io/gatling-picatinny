package org.galaxio.gatling.diagnostics

import java.lang.management.ManagementFactory

import org.galaxio.gatling.config.ConfigManager

import scala.jdk.CollectionConverters._

object Diagnostics {
  private val EnabledPath = "picatinny.diagnostics.enabled"

  def printIfEnabled(): Unit =
    if (isEnabled) println(render())

  private[gatling] def isEnabled: Boolean =
    ConfigManager.simulationConfig.get(EnabledPath, false)

  private[diagnostics] def render(): String = {
    val runtime = Runtime.getRuntime
    val mxBean  = ManagementFactory.getRuntimeMXBean
    val args    = mxBean.getInputArguments.asScala.mkString(" ")

    s"""Diagnostics
       |   java           : ${System.getProperty("java.version")} ${System.getProperty("java.vendor")}
       |   os             : ${System.getProperty("os.name")} ${System.getProperty("os.arch")}
       |   timezone       : ${java.time.ZoneId.systemDefault()}
       |   working dir    : ${System.getProperty("user.dir")}
       |   max heap       : ${Formatters.bytes(runtime.maxMemory())}
       |   total heap     : ${Formatters.bytes(runtime.totalMemory())}
       |   free heap      : ${Formatters.bytes(runtime.freeMemory())}
       |   jvm args       : ${if (args.isEmpty) "<empty>" else args}
       |""".stripMargin
  }

}
