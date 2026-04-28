package org.galaxio.gatling.diagnostics

import io.gatling.core.controller.inject.closed._
import io.gatling.core.controller.inject.open._

import scala.concurrent.duration._

private[gatling] object InjectionProfileParser {

  def fromOpen(steps: Iterable[OpenInjectionStep]): Option[WorkloadSettings] = {
    val segments = openSegments(flattenOpen(steps.toList))
    fromSegments("provided-open-injection", "rps", segments)
  }

  def fromClosed(steps: Iterable[ClosedInjectionStep]): Option[WorkloadSettings] = {
    val segments = closedSegments(flattenClosed(steps.toList))
    fromSegments("provided-closed-injection", "users", segments)
  }

  def javaOpen(steps: Array[io.gatling.javaapi.core.OpenInjectionStep]): Option[WorkloadSettings] =
    fromOpen(steps.iterator.map(_.asScala()).toList)

  def javaClosed(steps: Array[io.gatling.javaapi.core.ClosedInjectionStep]): Option[WorkloadSettings] =
    fromClosed(steps.iterator.map(_.asScala()).toList)

  private def fromSegments(profile: String, unit: String, segments: List[WorkloadSegment]): Option[WorkloadSettings] =
    segments.lastOption.map { last =>
      val maxLevel = math.max(0.01, segments.map(segment => math.max(segment.fromRps, segment.toRps)).max)
      WorkloadSettings(
        intensityRps = maxLevel,
        intensityText = s"${Formatters.decimal(maxLevel)} $unit",
        profile = WorkloadProfile.Provided(profile),
        stagesNumber = segments.count(segment => segment.kind == "stage" || segment.kind == "plateau"),
        rampDuration = segments.find(_.kind.contains("ramp")).map(segment => segment.end - segment.start).getOrElse(0.seconds),
        stageDuration =
          segments.filter(segment => segment.kind == "stage" || segment.kind == "plateau").foldLeft(0.seconds)(_ + _.duration),
        testDuration = last.end,
        unit = unit,
        segmentsOverride = Some(segments),
      )
    }

  private def openSegments(steps: List[OpenInjectionStep]): List[WorkloadSegment] =
    steps
      .foldLeft((List.empty[WorkloadSegment], 0.seconds, 0.0)) { case ((segments, cursor, currentRate), step) =>
        val (next, nextRate) = step match {
          case s: NothingForOpenInjection   =>
            List(WorkloadSegment(cursor, cursor + s.duration, "pause", 0.0, 0.0)) -> 0.0
          case s: AtOnceOpenInjection       =>
            List(WorkloadSegment(cursor, cursor, "at-once", currentRate, s.users.toDouble)) -> currentRate
          case s: RampOpenInjection         =>
            val rate = rateFromUsers(s.users, s.duration)
            List(WorkloadSegment(cursor, cursor + s.duration, "constant-users", rate, rate)) -> rate
          case s: ConstantRateOpenInjection =>
            List(WorkloadSegment(cursor, cursor + s.duration, "plateau", s.rate, s.rate)) -> s.rate
          case s: RampRateOpenInjection     =>
            List(WorkloadSegment(cursor, cursor + s.duration, "ramp", s.startRate, s.endRate)) -> s.endRate
          case s: PoissonOpenInjection      =>
            List(WorkloadSegment(cursor, cursor + s.duration, "randomized-ramp", s.startRate, s.endRate)) -> s.endRate
          case s: HeavisideOpenInjection    =>
            stressPeak(cursor, longField(s, 0), s.duration) -> currentRate
          case other                        =>
            val duration = durationField(other)
            val rate     = rateFromUsers(longField(other, 0), duration)
            List(WorkloadSegment(cursor, cursor + duration, other.productPrefix, rate, rate)) -> rate
        }
        (segments ++ next, cursor + durationField(step), nextRate)
      }
      ._1

  private def closedSegments(steps: List[ClosedInjectionStep]): List[WorkloadSegment] =
    steps
      .foldLeft((List.empty[WorkloadSegment], 0.seconds)) { case ((segments, cursor), step) =>
        val next = step match {
          case s: ConstantConcurrentUsersInjection =>
            val duration = durationField(s)
            List(WorkloadSegment(cursor, cursor + duration, "plateau", s.number.toDouble, s.number.toDouble))
          case s: RampConcurrentUsersInjection     =>
            val duration = durationField(s)
            List(WorkloadSegment(cursor, cursor + duration, "ramp", s.from.toDouble, s.to.toDouble))
          case other                               =>
            val duration = durationField(other)
            val start    = doubleField(other, 0)
            val end      = doubleField(other, math.max(0, other.productArity - 2))
            List(WorkloadSegment(cursor, cursor + duration, other.productPrefix, start, end))
        }
        (segments ++ next, cursor + durationField(step))
      }
      ._1

  private def flattenOpen(steps: List[OpenInjectionStep]): List[OpenInjectionStep] =
    steps.flatMap {
      case s: StairsUsersPerSecCompositeStep => openStairs(s)
      case step                              => List(step)
    }

  private def flattenClosed(steps: List[ClosedInjectionStep]): List[ClosedInjectionStep] =
    steps.flatMap {
      case s: StairsConcurrentUsersCompositeStep => closedStairs(s)
      case step                                  => List(step)
    }

  private def openStairs(step: StairsUsersPerSecCompositeStep): List[OpenInjectionStep] =
    (1 to step.levels).toList.flatMap { level =>
      val from = step.startingRate + step.rateIncrement * (level - 1)
      val to   = step.startingRate + step.rateIncrement * level
      List(
        RampRateOpenInjection(from, to, step.rampDuration),
        ConstantRateOpenInjection(to, step.duration),
      )
    }

  private def closedStairs(step: StairsConcurrentUsersCompositeStep): List[ClosedInjectionStep] =
    (1 to step.levels).toList.flatMap { level =>
      val from = step.startingUsers + step.usersIncrement * (level - 1)
      val to   = step.startingUsers + step.usersIncrement * level
      List(
        RampConcurrentUsersInjection(from, to, step.rampDuration),
        ConstantConcurrentUsersInjection(to, step.levelDuration),
      )
    }

  private def stressPeak(start: FiniteDuration, users: Long, duration: FiniteDuration): List[WorkloadSegment] = {
    val peak = rateFromUsers(users, duration) * 2
    val mid  = start + duration / 2
    List(
      WorkloadSegment(start, mid, "stress-peak", 0.0, peak),
      WorkloadSegment(mid, start + duration, "stress-peak", peak, 0.0),
    )
  }

  private def rateFromUsers(users: Long, duration: FiniteDuration): Double =
    users.toDouble / math.max(1.0, duration.toSeconds.toDouble)

  private def durationField(product: Product): FiniteDuration =
    product.productIterator.collectFirst { case duration: FiniteDuration => duration }.getOrElse(0.seconds)

  private def longField(product: Product, index: Int): Long =
    product.productElement(index) match {
      case value: Long => value
      case value: Int  => value.toLong
      case _           => 0L
    }

  private def doubleField(product: Product, index: Int): Double =
    product.productElement(index) match {
      case value: Double => value
      case value: Int    => value.toDouble
      case value: Long   => value.toDouble
      case _             => 0.0
    }

}
