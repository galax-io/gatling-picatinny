package org.galaxio.gatling.utils

import org.galaxio.gatling.diagnostics.{Diagnostics, StartupBanner}

object Utility {

  def banner(): Unit =
    StartupBanner.printIfEnabled()

  def diagnostics(): Unit =
    Diagnostics.printIfEnabled()

}
