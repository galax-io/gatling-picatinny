package org.galaxio.performance.picatinny

object Utility {
  def debugMemoryAndOpts(): Unit = {
    println(s"Runtime max memory: ${Runtime.getRuntime.maxMemory()}")
    println(s"JVM input arguments: ${java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments}")
  }
}
