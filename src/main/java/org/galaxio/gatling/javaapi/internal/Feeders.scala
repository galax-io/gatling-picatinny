package org.galaxio.gatling.javaapi.internal

import io.gatling.core.feeder._
import org.galaxio.gatling.feeders.{faker => fakerApi}

import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object Feeders {
  // Justification: Java generics bridge (Any -> Object), erasure-safe facade seam
  // scalafix:off DisableSyntax.asInstanceOf
  def toJavaFeeder[T](scalaFeeder: Feeder[T]): ju.Iterator[ju.Map[String, Object]] =
    scalaFeeder
      .map(_.asJava)
      .asJava
      .asInstanceOf[ju.Iterator[
        ju.Map[String, Object],
      ]]

  def toJavaFeeder(scalaFeeder: IndexedSeq[Record[String]]): ju.Iterator[ju.Map[String, Object]] =
    scalaFeeder
      .map(_.asJava)
      .asJava
      .iterator()
      .asInstanceOf[ju.Iterator[
        ju.Map[String, Object],
      ]]
  // scalafix:on DisableSyntax.asInstanceOf

  def toScalaOption[T](optionJava: ju.Optional[T]): Option[T] = optionJava.toScala

  def toScala(col: ju.List[ju.Map[String, Object]]): Seq[Map[String, Any]] = col.asScala.toSeq.map(x => x.asScala.toMap)

  def generatedFeeder(fields: ju.List[fakerApi.Field[_]]): ju.Iterator[ju.Map[String, Object]] =
    toJavaFeeder(fakerApi.GeneratedFeeder.apply(fields.asScala.toSeq: _*))

  def generatedFeederSingle[A](name: String, generator: fakerApi.Generator[A]): ju.Iterator[ju.Map[String, Object]] =
    toJavaFeeder(fakerApi.GeneratedFeeder.single(name, generator))
}
