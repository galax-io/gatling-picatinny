package org.galaxio.gatling.config

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.reflect.runtime.universe._
import scala.util.{Failure, Try}
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.scalalogging.LazyLogging

private[gatling] class SimulationConfigUtils(config: Config) extends LazyLogging {
  private val StringTag         = typeTag[String]
  private val FiniteDurationTag = typeTag[FiniteDuration]
  private val StringListTag     = typeTag[List[String]]
  private val BigDecimalTag     = typeTag[BigDecimal]
  private val ConfigTag         = typeTag[Config]

  def getOpt[T](path: String)(implicit tag: TypeTag[T]): Option[T] =
    if (config.hasPath(path)) Some(getValueByType(config, path))
    else None

  def get[T](path: String)(implicit tag: TypeTag[T]): T =
    getOpt(path).getOrElse {
      val message = s"Missing required simulation config value: $path. Define it in simulation.conf or pass -D$path=<value>."
      logger.error(message)
      throw new SimulationConfigException(message)
    }

  def get[T](path: String, default: => T)(implicit tag: TypeTag[T]): T =
    getOpt(path).getOrElse(default)

  def requirePositive(path: String, value: Int): Int =
    requireCondition(path, value, value > 0, "must be greater than 0")

  def requirePositive(path: String, value: Double): Double =
    requireCondition(path, value, value > 0, "must be greater than 0")

  def requirePositive(path: String, value: FiniteDuration): FiniteDuration =
    requireCondition(path, value, value > Duration.Zero, "must be greater than 0")

  def requireNonNegative(path: String, value: FiniteDuration): FiniteDuration =
    requireCondition(path, value, value >= Duration.Zero, "must be 0 or greater")

  private def getValueByType[T](cfg: Config, path: String)(implicit tag: TypeTag[T]): T =
    readValueByType(cfg, path)
      .map(_.asInstanceOf[T])
      .map { value =>
        logger.info(s"Simulation param for $path is set to: ${ConfigValueMasking.displayValue(path, value)}")
        value
      }
      .recoverWith(configReadFailure(path, tag))
      .get

  private def readValueByType(cfg: Config, path: String)(implicit tag: TypeTag[_]): Try[Any] =
    Try {
      tag match {
        case StringTag         => cfg.getString(path)
        case TypeTag.Long      => cfg.getLong(path)
        case TypeTag.Int       => cfg.getInt(path)
        case TypeTag.Double    => cfg.getDouble(path)
        case FiniteDurationTag => cfg.getDuration(path, TimeUnit.SECONDS).seconds
        case StringListTag     => cfg.getStringList(path).asScala.toList
        case TypeTag.Boolean   => cfg.getBoolean(path)
        case BigDecimalTag     => BigDecimal(cfg.getString(path))
        case ConfigTag         => cfg.getConfig(path)
        case _                 => throw unsupportedTypeException(path, tag)
      }
    }

  private def configReadFailure(path: String, tag: TypeTag[_]): PartialFunction[Throwable, Try[Nothing]] = {
    case e: SimulationConfigException => Failure(e)
    case e: ConfigException           => Failure(invalidValueException(path, tag, e))
    case e: NumberFormatException     => Failure(invalidValueException(path, tag, e))
  }

  private def invalidValueException(path: String, tag: TypeTag[_], cause: Throwable): SimulationConfigException = {
    val message = s"Invalid simulation config value at $path for expected type ${tag.tpe}: ${cause.getMessage}"
    logger.error(message)
    new SimulationConfigException(message, cause)
  }

  private def unsupportedTypeException(path: String, tag: TypeTag[_]): SimulationConfigException = {
    val message = s"Configuration option type $tag is not implemented for path $path"
    logger.error(message)
    new SimulationConfigException(message)
  }

  private def requireCondition[T](path: String, value: T, valid: Boolean, rule: String): T =
    if (valid) value
    else {
      val message = s"Invalid simulation config value at $path: $value ($rule)."
      logger.error(message)
      throw new SimulationConfigException(message)
    }

}

private[gatling] object SimulationConfigUtils {
  def apply(config: Config): SimulationConfigUtils = new SimulationConfigUtils(config)
}
