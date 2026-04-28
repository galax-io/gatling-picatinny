package org.galaxio.gatling.utils

import org.galaxio.gatling.diagnostics.{Diagnostics, StartupBanner}
import io.gatling.core.controller.inject.closed.ClosedInjectionStep
import io.gatling.core.controller.inject.open.OpenInjectionStep

/** Convenience diagnostics facade for Gatling simulations.
  *
  * Use `banner(injectionProfile)` when the simulation has explicit Gatling injection steps. Use `banner()` only when the banner
  * must fall back to `simulation.conf` workload parameters.
  */
object Utility {

  /** Prints the startup banner from `simulation.conf` workload settings when `picatinny.startup.banner.enabled` is true.
    */
  def banner(): Unit =
    StartupBanner.printIfEnabled()

  /** Prints the startup banner by parsing Scala Gatling open or closed injection steps.
    */
  def banner(steps: Iterable[_]): Unit =
    printFromValues(steps.toList)

  /** Prints the startup banner by parsing a single Scala Gatling open injection step.
    */
  def banner(step: OpenInjectionStep): Unit =
    StartupBanner.printOpenIfEnabled(List(step))

  /** Prints the startup banner by parsing a single Scala Gatling closed injection step.
    */
  def banner(step: ClosedInjectionStep): Unit =
    StartupBanner.printClosedIfEnabled(List(step))

  /** Prints the startup banner by parsing a tuple of Scala Gatling injection steps.
    */
  def banner(steps: Product): Unit =
    printFromValues(steps.productIterator.toList)

  /** Prints the startup banner by parsing Java/Kotlin Gatling open injection steps.
    */
  def banner(steps: Array[io.gatling.javaapi.core.OpenInjectionStep]): Unit =
    StartupBanner.printSettingsIfEnabled(org.galaxio.gatling.diagnostics.InjectionProfileParser.javaOpen(steps))

  /** Prints the startup banner by parsing Java/Kotlin Gatling closed injection steps.
    */
  def banner(steps: Array[io.gatling.javaapi.core.ClosedInjectionStep]): Unit =
    StartupBanner.printSettingsIfEnabled(org.galaxio.gatling.diagnostics.InjectionProfileParser.javaClosed(steps))

  private def printFromValues(values: List[Any]): Unit =
    values match {
      case values if values.forall(_.isInstanceOf[OpenInjectionStep])   =>
        StartupBanner.printOpenIfEnabled(values.collect { case step: OpenInjectionStep => step })
      case values if values.forall(_.isInstanceOf[ClosedInjectionStep]) =>
        StartupBanner.printClosedIfEnabled(values.collect { case step: ClosedInjectionStep => step })
      case _                                                            =>
        banner()
    }

  /** Prints runtime/JVM diagnostics when `picatinny.diagnostics.enabled` is true.
    */
  def diagnostics(): Unit =
    Diagnostics.printIfEnabled()

}
