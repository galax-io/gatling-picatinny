package org.galaxio.gatling.utils

object RandomDataGeneratorsWrapper {

  def randomIntValueFromJava(): Int = {
    IntRandomProvider.random()
  }

  def randomIntValueFromJava(max: Int): Int = {
    IntRandomProvider.random(max)
  }

  def randomIntValueFromJava(min: Int, max: Int): Int = {
    IntRandomProvider.random(min, max)
  }

  def randomLongValueFromJava(): Long = {
    LongRandomProvider.random()
  }

  def randomLongValueFromJava(max: Long): Long = {
    LongRandomProvider.random(max)
  }

  def randomLongValueFromJava(min: Long, max: Long): Long = {
    LongRandomProvider.random(min, max)
  }

  def randomFloatValueFromJava(): Float = {
    FloatRandomProvider.random()
  }

  def randomFloatValueFromJava(max: Float): Float = {
    FloatRandomProvider.random(max)
  }

  def randomFloatValueFromJava(min: Float, max: Float): Float = {
    FloatRandomProvider.random(min, max)
  }

  def randomDoubleValueFromJava(): Double = {
    DoubleRandomProvider.random()
  }

  def randomDoubleValueFromJava(max: Double): Double = {
    DoubleRandomProvider.random(max)
  }

  def randomDoubleValueFromJava(min: Double, max: Double): Double = {
    DoubleRandomProvider.random(min, max)
  }

}
