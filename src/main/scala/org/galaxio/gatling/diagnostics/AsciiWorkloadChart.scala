package org.galaxio.gatling.diagnostics

import scala.concurrent.duration._

private[diagnostics] object AsciiWorkloadChart {
  private val Width = 72
  private val Rows  = 10

  def render(settings: WorkloadSettings): String = {
    val totalSeconds = math.max(1.0, settings.testDuration.toSeconds.toDouble)
    val maxRps       = math.max(0.01, settings.intensityRps)
    val grid         = Array.fill(Rows, Width)(' ')

    WorkloadTimeline.segments(settings).foreach { segment =>
      val start = Point(xFor(segment.start, totalSeconds), yFor(segment.fromRps, maxRps))
      val end   = Point(xFor(segment.end, totalSeconds), yFor(segment.toRps, maxRps))

      segment.kind match {
        case "ramp" => drawRamp(grid, start, end)
        case _      => drawHorizontal(grid, start.x, end.x, end.y)
      }
    }

    val body = grid.zipWithIndex.map { case (chars, row) =>
      val level = maxRps * (Rows - 1 - row) / (Rows - 1)
      s"   ${Formatters.decimal(level).reverse.padTo(5, ' ').reverse} |${chars.mkString}"
    }
      .mkString("\n")

    s"""   rps
       |$body
       |         +${"-" * Width}
       |          00:00${" " * math.max(1, Width - 12)}${Formatters.time(settings.testDuration)}
       |""".stripMargin
  }

  private[diagnostics] def strokeColumns(chart: String): IndexedSeq[String] =
    chart.linesIterator
      .filter(_.contains("|"))
      .map(_.dropWhile(_ != '|').drop(1))
      .toIndexedSeq

  private[diagnostics] def hasContinuousStroke(chart: String): Boolean = {
    val rows = strokeColumns(chart)
    rows.nonEmpty && rows.map(_.length).minOption.exists { width =>
      (0 until width).forall(column => rows.exists(row => "_/|".contains(row.charAt(column))))
    }
  }

  private final case class Point(x: Int, y: Int)

  private def drawRamp(grid: Array[Array[Char]], start: Point, end: Point): Unit =
    if (start.x == end.x) drawVertical(grid, start.x, start.y, end.y)
    else {
      val fromX = math.min(start.x, end.x)
      val toX   = math.max(start.x, end.x)

      (fromX to toX).foreach { x =>
        val progress = (x - start.x).toDouble / (end.x - start.x).toDouble
        val y        = math.round(start.y + (end.y - start.y) * progress).toInt
        put(grid, x, y, '/')
      }
    }

  private def drawHorizontal(grid: Array[Array[Char]], startX: Int, endX: Int, y: Int): Unit =
    (math.min(startX, endX) to math.max(startX, endX)).foreach(x => put(grid, x, y, '_', overwrite = false))

  private def drawVertical(grid: Array[Array[Char]], x: Int, startY: Int, endY: Int): Unit =
    (math.min(startY, endY) to math.max(startY, endY)).foreach(y => put(grid, x, y, '|'))

  private def put(grid: Array[Array[Char]], x: Int, y: Int, char: Char, overwrite: Boolean = true): Unit =
    if (grid.indices.contains(y) && grid(y).indices.contains(x) && (overwrite || grid(y)(x) == ' ')) grid(y)(x) = char

  private def xFor(duration: FiniteDuration, totalSeconds: Double): Int = {
    val normalized = math.max(0.0, math.min(1.0, duration.toSeconds.toDouble / totalSeconds))
    math.round(normalized * (Width - 1)).toInt
  }

  private def yFor(level: Double, maxRps: Double): Int = {
    val normalized = math.max(0.0, math.min(1.0, level / maxRps))
    (Rows - 1) - math.round(normalized * (Rows - 1)).toInt
  }

}
