package org.galaxio.gatling

import java.util.concurrent.ThreadLocalRandom
import scala.util.Random

package object utils {

  def getRandomElement[T](seq: Seq[T]): T = seq(Random.nextInt(seq.length))

  implicit object IntRandomProvider extends RandomProvider[Int] {

    override def random(): Int = ThreadLocalRandom.current().nextInt()

    override def random(max: Int): Int = ThreadLocalRandom.current().nextInt(max)

    override def random(min: Int, max: Int): Int = ThreadLocalRandom.current().nextInt(min, max)

  }

  implicit object LongRandomProvider extends RandomProvider[Long] {
    override def random(): Long = ThreadLocalRandom.current().nextLong()

    override def random(max: Long): Long = ThreadLocalRandom.current().nextLong(max)

    override def random(min: Long, max: Long): Long = ThreadLocalRandom.current().nextLong(min, max)

  }

  implicit object FloatRandomProvider extends RandomProvider[Float] {
    override def random(): Float = ThreadLocalRandom.current().nextFloat()

    override def random(max: Float): Float = ThreadLocalRandom.current().nextFloat() * max

    override def random(min: Float, max: Float): Float =
      min + ThreadLocalRandom.current().nextFloat() * (max - min)
  }

  implicit object DoubleRandomProvider extends RandomProvider[Double] {
    override def random(): Double = ThreadLocalRandom.current().nextDouble()

    override def random(max: Double): Double = ThreadLocalRandom.current().nextDouble(max)

    override def random(min: Double, max: Double): Double =
      min + ThreadLocalRandom.current().nextDouble() * (max - min)
  }
}
