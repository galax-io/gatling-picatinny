package org.galaxio.gatling.utils

import com.eatthepath.uuid.FastUUID

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalUnit
import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.UUID
import scala.annotation.tailrec
import scala.util.Random

object RandomDataGenerators {

  private val Digits          = "0123456789"
  private val HexDigits       = "0123456789abcdef"
  private val CyrillicLetters = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"

  private def validateLength(stringLength: Int): Unit =
    require(stringLength > 0, s"String length must be > 0, but got $stringLength")

  private def randomStringFromAlphabet(alphabet: String, length: Int): String = {
    validateLength(length)
    randomString(alphabet)(length)
  }

  private def validateRange[T](min: T, max: T)(implicit ordering: Ordering[T]): Unit = {
    require(ordering.lteq(min, max), s"Min value ($min) must be less than max value ($max)")
  }

  /** Generates a random string of the specified length using the provided alphabet of characters.
    *
    * @param alphabet
    *   the set of characters to use when generating the random string; must be non-empty
    * @param length
    *   the desired length of the random string; must be greater than 0
    * @return
    *   a random string of specified length composed of characters from the provided alphabet
    * @throws IllegalArgumentException
    *   if `alphabet` is empty or `length` is less than or equal to 0
    */
  def randomString(alphabet: String)(length: Int): String = {
    require(alphabet.nonEmpty, "Alphabet must be non-empty")
    require(length > 0, s"String length must be > 0, but got $length")

    Iterator.continually(Random.nextInt(alphabet.length)).map(alphabet).take(length).mkString
  }

  /** Generates a random string of digits with the specified length.
    *
    * @param length
    *   the desired length of the digit string; must be greater than 0
    * @return
    *   a random string consisting only of numeric digits of the specified length
    * @throws IllegalArgumentException
    *   if `length` is less than or equal to 0
    */
  def digitString(length: Int): String =
    randomStringFromAlphabet(Digits, length)

  /** Generates a random hexadecimal string of the specified length.
    *
    * @param length
    *   the desired length of the hexadecimal string; must be greater than 0
    * @return
    *   a randomly generated string of the specified length containing only hexadecimal characters (0-9, a-f)
    * @throws IllegalArgumentException
    *   if the `length` is less than or equal to 0
    */
  def hexString(length: Int): String =
    randomStringFromAlphabet(HexDigits, length)

  /** Generates a random alphanumeric string of the specified length.
    *
    * @param length
    *   the desired length of the random string; must be greater than 0
    * @return
    *   a random string of the specified length composed of alphanumeric characters
    * @throws IllegalArgumentException
    *   if `length` is less than or equal to 0
    */
  def alphanumericString(length: Int): String =
    Random.alphanumeric.take(length).mkString

  /** Generates a random string of letters with the specified length.
    *
    * @param length
    *   the desired length of the string; must be greater than 0
    * @return
    *   a random string consisting only of alphabetic characters of the specified length
    * @throws IllegalArgumentException
    *   if `length` is less than or equal to 0
    */
  def lettersString(length: Int): String = {
    validateLength(length)
    Random.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  /** Generates a random string of the specified length containing only Cyrillic characters.
    *
    * @param length
    *   the desired length of the generated Cyrillic string; must be greater than 0
    * @return
    *   a random string of the specified length composed of Cyrillic characters
    * @throws IllegalArgumentException
    *   if `length` is less than or equal to 0
    */
  def cyrillicString(length: Int): String =
    randomStringFromAlphabet(CyrillicLetters, length)

  /** Generates a random UUID as a string.
    *
    * @return
    *   a randomly generated UUID in string format
    */
  def randomUUID: String = FastUUID.toString(UUID.randomUUID)

  /** Generates a random value of type `T` using the provided implicit `RandomProvider`.
    *
    * @tparam T
    *   the type of the random value to generate
    * @param rng
    *   the implicit `RandomProvider` instance used to generate the random value
    * @return
    *   a random value of type `T`
    */
  def randomValue[T]()(implicit rng: RandomProvider[T]): T = rng.random()

  /** Generates a random value of type `T` within the specified upper bound.
    *
    * @param max
    *   the maximum value for the random generation
    * @param rng
    *   an implicit `RandomProvider` instance used to generate the random value
    * @return
    *   a randomly generated value of type `T` that is less than or equal to `max`
    */
  def randomValue[T](max: T)(implicit rng: RandomProvider[T]): T = rng.random(max)

  /** Generates a random value of a specified type within a given range.
    *
    * @param min
    *   the minimum value of the range (inclusive)
    * @param max
    *   the maximum value of the range (inclusive)
    * @param rng
    *   an implicit RandomProvider to generate random values of type T
    * @param ord
    *   an implicit Ordering to compare the minimum and maximum values
    * @return
    *   a randomly generated value of type T within the specified range
    * @throws IllegalArgumentException
    *   if the minimum value is not less than the maximum value
    */
  def randomValue[T](min: T, max: T)(implicit rng: RandomProvider[T], ord: Ordering[T]): T = {
    validateRange(min, max)
    if (min != max)
      rng.random(min, max)
    else
      max
  }

  /** Selects a random element from the provided list of integers. If the list is empty, a random integer of the specified
    * length is generated and returned.
    *
    * @param items
    *   a list of integers to select a random element from; can be empty
    * @param intLength
    *   the number of digits for generating a random integer if the list is empty
    * @return
    *   a random integer from the list if it is non-empty, or a randomly generated integer of the specified length if the list
    *   is empty
    */
  private def getRandomElement(items: List[Int], intLength: Int): Int = items match {
    case Nil => randomValue(intLength)
    case _   => items(randomValue(items.length))
  }

  /** Selects a random element from the given list of strings or generates a random string if the list is empty.
    *
    * @param items
    *   the list of strings to select a random element from; can be empty
    * @param stringLength
    *   the length of the random string to generate if the list is empty; must be greater than 0
    * @return
    *   a randomly selected string from the list or a randomly generated string of the specified length
    * @throws IllegalArgumentException
    *   if `stringLength` is less than or equal to 0
    */
  private def getRandomElement(items: List[String], stringLength: Int): String = items match {
    case Nil => lettersString(stringLength)
    case _   => items(randomValue(items.length))
  }

  def randomPAN(bins: String*): String = {
    val idNum: String = digitString(9)

    def fifteenDigits(bins: List[String]): List[Char] = bins match {
      case Nil => s"""${digitString(6)}$idNum""".toList
      case _   => s"""${getRandomElement(bins, 6)}$idNum""".toList
    }

    val results: List[Int] = fifteenDigits(bins.toList).flatMap(_.toString.toIntOption)
    val evenPosSum: Int    = results.indices.collect {
      case i if i % 2 == 0 => results(i)
    }.fold(0)((x, y) => x + (if (y * 2 > 9) y * 2 - 9 else y * 2))
    val oddPosSum: Int     = results.indices.collect { case i if i % 2 != 0 => results(i) }.sum
    val controlNum: Int    = 10 - (if ((oddPosSum + evenPosSum) % 10 == 0) 10 else (oddPosSum + evenPosSum) % 10)

    s"""${results.mkString("")}$controlNum"""
  }

  def randomOGRN(): String = {
    val indicatorOGRN: Int   = getRandomElement(List(1, 5), 1)
    val year: String         = String.format("%02d", randomValue(2, 21))
    val ruSubjectNum: String = String.format("%02d", randomValue(1, 90))
    val idNum: String        = digitString(7)
    val result: String       = s"""$indicatorOGRN$year$ruSubjectNum$idNum"""
    val rem: Long            = result.toLong % 11

    if (rem == 10)
      s"""${result}0"""
    else
      s"""$result$rem"""
  }

  def randomPSRNSP(): String = {
    val indicatorPSRNSP: Int = 3
    val year: String         = String.format("%02d", randomValue(2, 21))
    val ruSubjectNum: String = String.format("%02d", randomValue(1, 90))
    val idNum: String        = digitString(9)
    val result: String       = s"""$indicatorPSRNSP$year$ruSubjectNum$idNum"""
    val rem: Long            = result.toLong % 13 % 10

    if (rem == 10)
      s"""${result}0"""
    else
      s"""$result$rem"""
  }

  def randomKPP(): String = {
    val revenueServiceCode: String = String.format("%04d", randomValue(1, 10000))
    val reasonForReg: String       = String.format("%02d", randomValue(1, 100))
    val idNum: String              = String.format("%03d", randomValue(1, 1000))

    s"""$revenueServiceCode$reasonForReg$idNum"""
  }

  def randomNatITN(): String = {

    @tailrec
    def itnNatRecursion(n: Int, sum: Int, results: List[Int]): String = {
      val rnd: Int = results match {
        case 0 :: Nil => randomValue(1, 10)
        case _        => randomValue(0, 10)
      }

      val factors: List[Int] = List(2, 4, 10, 3, 5, 9, 4, 6, 8)

      def checkSum: Int = sum + rnd * factors(9 - n)

      n match {
        case 1 => (results :+ rnd :+ (if (checkSum % 11 == 10) 0 else checkSum % 11)).mkString("")
        case _ => itnNatRecursion(n - 1, checkSum, results :+ rnd)
      }
    }

    itnNatRecursion(9, 0, List.empty[Int])
  }

  def randomJurITN(): String = {

    @tailrec
    def itnJurRecursion(n: Int, sum1: Int, sum2: Int, results: List[Int]): String = {
      val rnd: Int                 = randomValue(0, 10)
      val firstFactors: List[Int]  = List(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
      val secondFactors: List[Int] = List(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)

      def checkSum1: Int = sum1 + rnd * firstFactors(11 - n)

      def checkSum2: Int = sum2 + rnd * secondFactors(11 - n)

      n match {
        case 1 =>
          (results :+ (if ((sum2 + results.last * secondFactors(11 - n)) % 11 == 10) 0
                       else
                         (sum2 + results.last * secondFactors(11 - n))   % 11)).mkString("")
        case 2 =>
          itnJurRecursion(
            n - 1,
            checkSum1,
            checkSum2,
            results :+ rnd :+ (if (checkSum1 % 11 == 10) 0
                               else
                                 checkSum1   % 11),
          )
        case _ => itnJurRecursion(n - 1, checkSum1, checkSum2, results :+ rnd)
      }
    }

    itnJurRecursion(11, 0, 0, List.empty[Int])
  }

  def randomSNILS(): String = {

    @tailrec
    def snilsRecursion(n: Int, sum: Int, results: List[Int]): String = {
      val rnd: Int = randomValue(0, 10)

      def checkSum: Int = sum + rnd * n

      @tailrec
      def defineCheckSum(checkSum: Int): String = checkSum match {
        case x if x >= 10 && x < 100 => checkSum.toString
        case x if x < 10             => s"0$checkSum"
        case 100 | 101               => "00"
        case _                       => defineCheckSum(checkSum % 101)
      }

      n match {
        case 1 => (results :+ rnd :+ defineCheckSum(checkSum)).mkString("")
        case _ => snilsRecursion(n - 1, checkSum, results :+ rnd)
      }
    }

    snilsRecursion(9, 0, List.empty[Int])
  }

  def randomRusPassport(): String = {
    val ruSubjectNum: String = String.format("%02d", randomValue(1, 90))
    val year: String         = String.format("%02d", randomValue(0, 21))
    val idNum: String        = digitString(6)

    s"""$ruSubjectNum$year$idNum"""
  }

  private def validateDelta(positiveDelta: Int, negativeDelta: Int): Unit = {
    require(
      positiveDelta >= 0 && negativeDelta >= 0,
      s"RandomDateFeeder delta requires values >0. Current values: positiveDelta= $positiveDelta, negativeDelta= $negativeDelta",
    )
  }

  /** Generates a random date string based on the provided parameters.
    *
    * @param positiveDelta
    *   the maximum positive offset in `unit` from the base date; must be non-negative
    * @param negativeDelta
    *   the maximum negative offset in `unit` from the base date; must be non-negative
    * @param datePattern
    *   the format pattern for the resulting date string
    * @param dateFrom
    *   the base date and time from which the random date is calculated
    * @param unit
    *   the temporal unit for the offset (e.g., days, hours, etc.)
    * @param timezone
    *   the timezone used when formatting the resulting date
    * @return
    *   a string representation of the randomly generated date formatted according to `datePattern`
    * @throws IllegalArgumentException
    *   if `positiveDelta` or `negativeDelta` is negative
    */
  def randomDate(
      positiveDelta: Int,
      negativeDelta: Int,
      datePattern: String,
      dateFrom: LocalDateTime,
      unit: TemporalUnit,
      timezone: ZoneId,
  ): String = {
    validateDelta(positiveDelta, negativeDelta)
    dateFrom
      .plus(randomValue(-negativeDelta, positiveDelta), unit)
      .atZone(timezone)
      .format(DateTimeFormatter.ofPattern(datePattern))
  }

  /** Generates a random date based on the specified parameters.
    *
    * @param offsetDate
    *   the offset in units to adjust the date; must not be 0
    * @param datePattern
    *   the format pattern for the resulting date string; defaults to "yyyy-MM-dd"
    * @param dateFrom
    *   the base datetime from which to calculate the random date
    * @param unit
    *   the temporal unit to use for offset (e.g., days, hours, etc.)
    * @param timezone
    *   the timezone to apply when formatting the date
    * @return
    *   a string representation of the randomly generated date formatted according to `datePattern`
    * @throws IllegalArgumentException
    *   if `offsetDate` is 0
    */
  def randomDate(
      offsetDate: Long,
      datePattern: String = "yyyy-MM-dd",
      dateFrom: LocalDateTime,
      unit: TemporalUnit,
      timezone: ZoneId,
  ): String = {
    require(offsetDate != 0, s"RandomRangeDateFeeder offset cannot be zero. Current value: offsetDate= $offsetDate")

    val adjustedDate = if (offsetDate > 0) {
      dateFrom.plus(randomValue(1L, offsetDate), unit)
    } else {
      dateFrom.minus(randomValue(1L, math.abs(offsetDate)), unit)
    }

    adjustedDate.atZone(timezone).format(DateTimeFormatter.ofPattern(datePattern))
  }

  /** Returns the current date and time formatted according to the provided date pattern and timezone.
    *
    * @param datePattern
    *   the DateTimeFormatter specifying the pattern to format the date and time
    * @param timezone
    *   the ZoneId representing the timezone to apply when formatting
    * @return
    *   a string representation of the current date and time formatted as per the specified pattern and timezone
    */
  def currentDate(datePattern: DateTimeFormatter, timezone: ZoneId): String = {
    Instant.now.atZone(timezone).format(datePattern)
  }

}
