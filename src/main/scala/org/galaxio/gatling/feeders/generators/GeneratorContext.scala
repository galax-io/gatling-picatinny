package org.galaxio.gatling.feeders.generators

import scala.util.Random

final case class GeneratorContext(gen: Random, sizeBounds: SizeBounds, daysOffset: Int)

object GeneratorContext {
  val default: GeneratorContext = {
    val jgen = new java.util.Random()
    GeneratorContext(Random.javaRandomToRandom(jgen), SizeBounds.default, 30)
  }
}
