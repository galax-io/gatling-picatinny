package org.galaxio.gatling.diagnostics

import java.lang.management.ManagementFactory

import com.typesafe.scalalogging.StrictLogging
import org.galaxio.gatling.config.{ConfigManager, ConfigValueMasking}

import scala.jdk.CollectionConverters._

object Diagnostics extends StrictLogging {
  private val EnabledPath = "picatinny.diagnostics.enabled"

  def printIfEnabled(): Unit =
    if (isEnabled) logger.info(render())

  private[gatling] def isEnabled: Boolean =
    ConfigManager.simulationConfig.get(EnabledPath, false)

  /** Redact the value of a sensitive `-Dkey=value` (or `-Dkey:value`) JVM argument, keeping the key visible. Uses the SAME
    * config-aware masking instance as config logging, so `picatinny.redaction.additionalSensitiveKeys` / `replacement` also
    * apply to JVM args. Non-`-D` or value-less args pass through unchanged.
    */
  private[diagnostics] def redactArg(arg: String, masking: ConfigValueMasking): String =
    Option(arg)
      .filter(_.startsWith("-D"))
      .map(_.drop(2))
      .flatMap(splitKeyValue)
      .collect {
        case (key, separator) if masking.isSensitive(key) => s"-D$key$separator${masking.placeholder}"
      }
      .getOrElse(arg)

  /** Split a `-D` body into `(key, separator)` at the first `=`/`:`, or `None` when there is no value (key stays intact). */
  private def splitKeyValue(body: String): Option[(String, Char)] =
    body.indexWhere(c => c == '=' || c == ':') match {
      case idx if idx > 0 => Some((body.take(idx), body.charAt(idx)))
      case _              => None
    }

  private[diagnostics] def render(): String = {
    val runtime = Runtime.getRuntime
    val mxBean  = ManagementFactory.getRuntimeMXBean
    val masking = ConfigManager.simulationConfig.masking
    val args    = mxBean.getInputArguments.asScala.map(redactArg(_, masking)).mkString(" ")

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
