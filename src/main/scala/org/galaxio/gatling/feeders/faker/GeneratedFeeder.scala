package org.galaxio.gatling.feeders.faker

import io.gatling.core.feeder.{Feeder, Record}

/** Gatling feeder bridge for faker generators.
  *
  * Use this object when generated values need to become Gatling session variables. Heterogeneous records are represented as
  * `Feeder[Any]`, matching how Gatling scenarios usually combine strings, numbers, booleans, and dates in the same session map.
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
    feeder.map { record =>
      // Safe: Record[A] = Map[String, A]; widening A → Any is always valid at runtime.
      // Map is invariant in Scala so the compiler requires asInstanceOf, but the cast
      // cannot fail because Map[String, A] is-a Map[String, Any] on the JVM (erasure).
      widenRecord(record) + (name -> generator.sample())
    }
  }

  /** Adds several generated fields to every record produced by an existing Gatling feeder. */
  def withGenerated[A](feeder: Feeder[A], fields: Field[_]*): Feeder[Any] = {
    require(fields.nonEmpty, "Generated feeder requires at least one field")
    feeder.map { record =>
      widenRecord(record) ++ fields.map(_.sampleAny)
    }
  }

  /** Widen `Record[A]` (= `Map[String, A]`) to `Map[String, Any]`.
    *
    * Safe because `Map[String, A]` is always a subtype of `Map[String, Any]` at runtime (JVM erasure). Scala's `Map` is
    * invariant in its value type, so the compiler cannot prove this statically, but the cast is guaranteed to succeed for any
    * `A`.
    */
  private def widenRecord[A](record: Record[A]): Map[String, Any] =
    record.asInstanceOf[Map[String, Any]]

  /** Materializes records so Gatling's built-in feeder strategies can be used. */
  def recordsFrom(records: Iterable[Map[String, Any]]): IndexedSeq[Record[Any]] = {
    val vector = records.map(_.toMap).toIndexedSeq
    require(vector.nonEmpty, "Materialized feeder requires at least one record")
    vector
  }
}
