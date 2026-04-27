package org.galaxio.performance

package object picatinny {
  if (sys.env.get("DEBUG").exists(_.equalsIgnoreCase("true")))
    Utility.debugMemoryAndOpts()
}
