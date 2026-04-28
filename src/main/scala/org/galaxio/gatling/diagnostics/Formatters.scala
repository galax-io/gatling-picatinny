package org.galaxio.gatling.diagnostics

import java.util.Locale

import scala.concurrent.duration.FiniteDuration

private[diagnostics] object Formatters {

  def decimal(value: Double): String =
    String.format(Locale.US, "%.2f", Double.box(value))

  def duration(duration: FiniteDuration): String = {
    val seconds = math.max(0L, duration.toSeconds)
    if (seconds == 0L) "0s"
    else {
      val hours   = seconds / 3600
      val minutes = (seconds % 3600) / 60
      val secs    = seconds  % 60

      List(hours -> "h", minutes -> "m", secs -> "s").collect { case (value, suffix) if value > 0 => s"$value$suffix" }
        .mkString(" ")
    }
  }

  def time(duration: FiniteDuration): String = {
    val seconds = math.max(0L, duration.toSeconds)
    val hours   = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs    = seconds  % 60

    if (hours > 0) f"$hours%02d:$minutes%02d:$secs%02d"
    else f"$minutes%02d:$secs%02d"
  }

  def bytes(value: Long): String = {
    val mb = value.toDouble / 1024.0 / 1024.0
    if (mb >= 1024.0) f"${mb / 1024.0}%.2f g" else f"$mb%.0f m"
  }

}
