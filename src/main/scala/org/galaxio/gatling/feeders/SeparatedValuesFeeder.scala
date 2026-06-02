package org.galaxio.gatling.feeders

import io.gatling.core.Predef._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.feeder.{FeederBuilderBase, _}

import scala.collection.mutable.ArrayBuffer

object SeparatedValuesFeeder {

  private val CommaSeparator: Char      = ','
  private val SemicolonSeparator: Char  = ';'
  private val TabulationSeparator: Char = '\t'

  /** Creates a feeder with separated values from the source String
    * @param paramName
    *   feeder name
    * @param source
    *   data source
    * @param separator
    *   ',', ';', '\t' or other delimiter which separates values.
    *
    * You also can use following methods for the most common separators: .csv(...), .ssv(...), .tsv(...)
    * @return
    *   a new feeder
    * @example
    *   {{{
    *   val sourceString = "v21;v22;v23"
    *   val separatedValuesFeeder: FeederBuilderBase[String] =
    *     SeparatedValuesFeeder("someValues", sourceString, ';') // this will return Vector(Map(someValues -> v21), Map(someValues -> v22), Map(someValues -> v23))
    *   }}}
    */
  def apply(paramName: String, source: String, separator: Char): IndexedSeq[Record[String]] = {
    val parts = source.split(separator)
    require(
      parts.nonEmpty,
      s"SeparatedValuesFeeder('$paramName'): source string produced no records after splitting by '$separator'. " +
        "Check that the source string is not empty and contains the expected separator.",
    )
    val buf   = new ArrayBuffer[Record[String]](parts.length)
    var i     = 0
    while (i < parts.length) {
      buf += Map(paramName -> parts(i).trim)
      i += 1
    }
    buf.toVector
  }

  /** Creates a feeder with separated values from the source Sequence
    * @param paramName
    *   feeder name
    * @param source
    *   data source
    * @param separator
    *   ',', ';', '\t' or other delimiter which separates values.
    *
    * You also can use following methods for the most common separators: .csv(...), .ssv(...), .tsv(...)
    * @return
    *   a new feeder
    * @example
    *   {{{
    *   val sourceSeq = Seq("1,two", "3,4")
    *   val separatedValuesFeeder: FeederBuilderBase[String] =
    *     SeparatedValuesFeeder.csv("someValues", sourceSeq) // this will return Vector(Map(someValues -> 1), Map(someValues -> two), Map(someValues -> 3), Map(someValues -> 4))
    *   }}}
    */
  def apply(paramName: String, source: Seq[String], separator: Char)(implicit
      configuration: GatlingConfiguration,
  ): IndexedSeq[Record[String]] = {
    val buf = ArrayBuffer.empty[Record[String]]
    source.foreach { s =>
      val parts = s.split(separator)
      var i     = 0
      while (i < parts.length) {
        buf += Map(paramName -> parts(i).trim)
        i += 1
      }
    }
    require(
      buf.nonEmpty,
      s"SeparatedValuesFeeder('$paramName'): source sequence is empty or produced no records after splitting by '$separator'. " +
        "Check that the source sequence contains at least one non-empty element.",
    )
    buf.toVector
  }

  /** Creates a feeder with separated values from the source Seq[Map[String, String] ]
    * @param paramPrefix
    *   feeder name
    * @param source
    *   data source
    * @param separator
    *   ',', ';', '\t' or other delimiter which separates values.
    *
    * You also can use following methods for the most common separators: .csv(...), .ssv(...), .tsv(...)
    * @return
    *   a new feeder
    * @example
    *   {{{
    *   val vaultFeeder: FeederBuilderBase[String] = Vector(
    *     Map(
    *       "HOSTS" -> "host11,host12",
    *       "USERS" -> "user11",
    *     ),
    *     Map(
    *       "HOSTS" -> "host21,host22",
    *       "USERS" -> "user21,user22,user23",
    *     ),
    *   )
    *   val mapFee: FeederBuilderBase[String] = SeparatedValuesFeeder(None, vaultFeeder.readRecords, ',')
    *   val separatedValuesFeeder: FeederBuilderBase[String] =
    *     SeparatedValuesFeeder("prefix", sourceSeq, ',') // this will return Vector(Map(HOSTS -> host11), Map(HOSTS -> host12), Map(USERS -> user11), Map(HOSTS -> host21), Map(HOSTS -> host22), Map(USERS -> user21), Map(USERS -> user22), Map(USERS -> user23))
    *   }}}
    */
  def apply(paramPrefix: Option[String], source: Seq[Map[String, Any]], separator: Char)(implicit
      configuration: GatlingConfiguration,
  ): IndexedSeq[Record[String]] = {
    val buf = ArrayBuffer.empty[Record[String]]
    source.foreach { m =>
      m.foreach { case (k, v) =>
        val key   = paramPrefix.fold(k)(pfx => s"${pfx}_$k")
        val parts = String.valueOf(v).split(separator)
        var i     = 0
        while (i < parts.length) {
          buf += Map(key -> parts(i))
          i += 1
        }
      }
    }
    require(
      buf.nonEmpty,
      s"SeparatedValuesFeeder(prefix=${paramPrefix.getOrElse("none")}): " +
        s"source map sequence is empty or produced no records after splitting by '$separator'. " +
        "Check that the source contains at least one non-empty map entry.",
    )
    buf.toVector
  }

  def csv(paramName: String, source: String): FeederBuilderBase[String] = apply(paramName, source, CommaSeparator)
  def ssv(paramName: String, source: String): FeederBuilderBase[String] = apply(paramName, source, SemicolonSeparator)
  def tsv(paramName: String, source: String): FeederBuilderBase[String] = apply(paramName, source, TabulationSeparator)

  def csv(paramName: String, source: Seq[String]): FeederBuilderBase[String] = apply(paramName, source, CommaSeparator)
  def ssv(paramName: String, source: Seq[String]): FeederBuilderBase[String] = apply(paramName, source, SemicolonSeparator)
  def tsv(paramName: String, source: Seq[String]): FeederBuilderBase[String] = apply(paramName, source, TabulationSeparator)

  def csv(paramPrefix: Option[String] = None, source: Seq[Map[String, Any]]): FeederBuilderBase[String] =
    apply(paramPrefix, source, CommaSeparator)
  def ssv(paramPrefix: Option[String] = None, source: Seq[Map[String, Any]]): FeederBuilderBase[String] =
    apply(paramPrefix, source, SemicolonSeparator)
  def tsv(paramPrefix: Option[String] = None, source: Seq[Map[String, Any]]): FeederBuilderBase[String] =
    apply(paramPrefix, source, TabulationSeparator)
}
