package org.galaxio.gatling.utils

import com.eatthepath.uuid.FastUUID

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalUnit
import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.UUID
import scala.util.Random
import org.galaxio.gatling.feeders.faker.GovIdGenerators

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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
    *   the exclusive upper bound for the random generation
    * @param rng
    *   an implicit `RandomProvider` instance used to generate the random value
    * @return
    *   a randomly generated value of type `T` that is strictly less than `max` (max exclusive)
    */
  def randomValue[T](max: T)(implicit rng: RandomProvider[T]): T = rng.random(max)

  /** Generates a random value of a specified type within a given range.
    *
    * @param min
    *   the minimum value of the range (inclusive)
    * @param max
    *   the maximum value of the range (exclusive)
    * @param rng
    *   an implicit RandomProvider to generate random values of type T
    * @param ord
    *   an implicit Ordering to compare the minimum and maximum values
    * @return
    *   a randomly generated value of type T within the range `[min, max)` (min inclusive, max exclusive); returns `max` only in
    *   the degenerate `min == max` case
    * @throws java.lang.IllegalArgumentException
    *   if the minimum value is not less than the maximum value
    */
  def randomValue[T](min: T, max: T)(implicit rng: RandomProvider[T], ord: Ordering[T]): T = {
    validateRange(min, max)
    if (min != max)
      rng.random(min, max)
    else
      max
  }

  @deprecated("Use Faker.finance.pan (with GeneratedFeeder) instead", "faker-api")
  def randomPAN(bins: String*): String = GovIdGenerators.pan(bins: _*)

  @deprecated("Use Faker.ru.ogrn (with GeneratedFeeder) instead", "faker-api")
  def randomOGRN(): String = GovIdGenerators.ogrn()

  @deprecated("Use Faker.ru.ogrnip (with GeneratedFeeder) instead", "faker-api")
  def randomPSRNSP(): String = GovIdGenerators.ogrnip()

  @deprecated("Use Faker.ru.kpp (with GeneratedFeeder) instead", "faker-api")
  def randomKPP(): String = GovIdGenerators.kpp()

  @deprecated("Use Faker.ru.inn.person (with GeneratedFeeder) instead", "faker-api")
  def randomNatITN(): String = GovIdGenerators.natITN()

  @deprecated("Use Faker.ru.inn.company (with GeneratedFeeder) instead", "faker-api")
  def randomJurITN(): String = GovIdGenerators.jurITN()

  @deprecated("Use Faker.ru.snils (with GeneratedFeeder) instead", "faker-api")
  def randomSNILS(): String = GovIdGenerators.snils()

  @deprecated("Use Faker.passport.ru (with GeneratedFeeder) instead", "faker-api")
  def randomRusPassport(): String = GovIdGenerators.rusPassport()

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
    * @throws java.lang.IllegalArgumentException
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
    * @throws java.lang.IllegalArgumentException
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
