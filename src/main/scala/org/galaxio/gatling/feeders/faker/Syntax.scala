package org.galaxio.gatling.feeders.faker

import io.gatling.core.feeder.{Feeder, Record}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

/** User-facing syntax for faker generators and generated feeders. */
trait Syntax {

  implicit final def tupleToField[A](field: (String, Generator[A])): Field[A] =
    Field(field._1, field._2)

  implicit final def generatorOps[A](generator: Generator[A]): Syntax.GeneratorOps[A] =
    new Syntax.GeneratorOps(generator)

  implicit final def localDateGeneratorOps(generator: Generator[LocalDate]): Syntax.LocalDateGeneratorOps =
    new Syntax.LocalDateGeneratorOps(generator)

  implicit final def localDateTimeGeneratorOps(generator: Generator[LocalDateTime]): Syntax.LocalDateTimeGeneratorOps =
    new Syntax.LocalDateTimeGeneratorOps(generator)

  implicit final def feederOps[A](feeder: Feeder[A]): Syntax.FeederOps[A] =
    new Syntax.FeederOps(feeder)

  implicit final def iterableOps[A](values: Iterable[A]): Syntax.IterableOps[A] =
    new Syntax.IterableOps(values)

  implicit final def mapOps(record: Map[String, Any]): Syntax.MapOps =
    new Syntax.MapOps(record)
}

object Syntax {

  final class GeneratorOps[A](private val generator: Generator[A]) extends AnyVal {

    /** Turns this generator into a typed single-field Gatling feeder. */
    def toFeeder(name: String): Feeder[A] =
      GeneratedFeeder.single(name, generator)
  }

  final class LocalDateGeneratorOps(private val generator: Generator[LocalDate]) extends AnyVal {

    /** Formats generated local dates with a Java time pattern. */
    def format(pattern: String): Generator[String] =
      generator.map(_.format(DateTimeFormatter.ofPattern(pattern)))
  }

  final class LocalDateTimeGeneratorOps(private val generator: Generator[LocalDateTime]) extends AnyVal {

    /** Formats generated local date-times with a Java time pattern. */
    def format(pattern: String): Generator[String] =
      generator.map(_.format(DateTimeFormatter.ofPattern(pattern)))
  }

  final class FeederOps[A](private val feeder: Feeder[A]) extends AnyVal {

    /** Adds a generated field to each record emitted by this feeder. */
    def withGenerated[B](name: String, generator: Generator[B]): Feeder[Any] =
      GeneratedFeeder.withGenerated(feeder, name, generator)

    /** Renames a key in each feeder record when the key is present. */
    def rename(from: String, to: String): Feeder[Any] =
      feeder.map { record =>
        val asAny = record.asInstanceOf[Map[String, Any]]
        asAny.get(from).fold(asAny)(value => asAny - from + (to -> value))
      }

    /** Prefixes every key in every feeder record. */
    def prefixKeys(prefix: String): Feeder[Any] =
      feeder.map(record => record.asInstanceOf[Map[String, Any]].map { case (key, value) => s"$prefix$key" -> value })

    /** Applies a typed transformation at the record boundary. */
    def mapRecord(f: Map[String, Any] => Map[String, Any]): Feeder[Any] =
      feeder.map(record => f(record.asInstanceOf[Map[String, Any]]))

    /** Materializes a finite number of records for assertions or small in-memory feeder sources. */
    def takeRecords(n: Int): Vector[Map[String, Any]] = {
      require(n >= 0, s"n must be >= 0: $n")
      feeder.take(n).map(_.asInstanceOf[Map[String, Any]]).toVector
    }
  }

  final class IterableOps[A](private val values: Iterable[A]) extends AnyVal {

    /** Converts collection items into single-field records compatible with Gatling feeder strategies. */
    def toFeeder(name: String): IndexedSeq[Record[Any]] =
      GeneratedFeeder.recordsFrom(values.map(value => Map(name -> value)))

    /** Converts collection items into records compatible with Gatling feeder strategies. */
    def toFeeder(mapping: A => Map[String, Any]): IndexedSeq[Record[Any]] =
      GeneratedFeeder.recordsFrom(values.map(mapping))
  }

  final class MapOps(private val record: Map[String, Any]) extends AnyVal {

    /** Converts one map into a one-record feeder builder. */
    def toSingleRecordFeeder: IndexedSeq[Record[Any]] =
      GeneratedFeeder.recordsFrom(Vector(record))
  }
}
