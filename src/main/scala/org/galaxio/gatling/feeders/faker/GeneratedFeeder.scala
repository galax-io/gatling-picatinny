package org.galaxio.gatling.feeders.faker

import io.gatling.core.feeder.{Feeder, Record}

/** Gatling feeder bridge for faker generators.
  *
  * Use this object when generated values need to become Gatling session
  * variables. Heterogeneous records are represented as `Feeder[Any]`, matching
  * how Gatling scenarios usually combine strings, numbers, booleans, and dates
  * in the same session map.
  */
object GeneratedFeeder {

  /** Builds an infinite feeder from named generators.
    *
    * @example
    *   {{{
    *   val users = GeneratedFeeder(
    *     "email" -> Faker.internet.email(),
    *     "amount" -> Faker.finance.amount(100, 5000)
    *   )
    *   }}}
    */
  def apply(fields: Field[_]*): Feeder[Any] = {
    require(fields.nonEmpty, "Generated feeder requires at least one field")
    Iterator.continually(fields.map(_.sampleAny).toMap)
  }

  /** Alias for `GeneratedFeeder(...)` when named construction reads better. */
  def generated(fields: Field[_]*): Feeder[Any] =
    apply(fields: _*)

  /** Builds an infinite single-field feeder without widening the generated value type. */
  def single[A](name: String, generator: Generator[A]): Feeder[A] = {
    require(name.nonEmpty, "Generated feeder field name must be non-empty")
    Iterator.continually(Map(name -> generator.sample()))
  }

  /** Builds an infinite feeder from a whole-record generator. */
  def records(generator: Generator[Map[String, Any]]): Feeder[Any] =
    Iterator.continually(generator.sample())

  /** Adds one generated field to every record produced by an existing Gatling feeder. */
  def withGenerated[A, B](feeder: Feeder[A], name: String, generator: Generator[B]): Feeder[Any] = {
    require(name.nonEmpty, "Generated feeder field name must be non-empty")
    feeder.map(record => record.asInstanceOf[Map[String, Any]] + (name -> generator.sample()))
  }

  /** Materializes records so Gatling's built-in feeder strategies can be used. */
  def recordsFrom(records: Iterable[Map[String, Any]]): IndexedSeq[Record[Any]] = {
    val vector = records.map(_.toMap).toIndexedSeq
    require(vector.nonEmpty, "Materialized feeder requires at least one record")
    vector
  }
}
