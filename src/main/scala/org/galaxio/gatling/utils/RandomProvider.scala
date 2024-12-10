package org.galaxio.gatling.utils

trait RandomProvider[T] {

  def random(): T

  def random(max: T): T

  def random(min: T, max: T): T

}
