package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.utils._
import org.galaxio.gatling.utils.RandomDataGenerators.{digitString, lettersString, randomValue}

/** Hand-rolled government / financial identifier generators with valid control digits (Russian INN, OGRN, OGRNIP, SNILS, KPP,
  * passport, and the Luhn-checked credit-card PAN).
  *
  * This is the internal source of truth behind the `Faker` DSL (`Faker.ru.*`, `Faker.finance.pan`, `Faker.rusPassport`). The
  * deprecated per-ID feeders and `RandomDataGenerators.random*` methods delegate here too. Not part of the public API.
  */
private[gatling] object GovIdGenerators {

  // Check-digit weight tables (FTS algorithm) — read-only, shared across virtual users, allocated once.
  private val NatInnFactors1: Array[Int] = Array(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
  private val NatInnFactors2: Array[Int] = Array(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
  private val JurInnFactors: Array[Int]  = Array(2, 4, 10, 3, 5, 9, 4, 6, 8)

  /** Left-pad a non-negative int with zeros to `width` (cheap replacement for `String.format("%0Nd")`). */
  private def zeroPad(value: Int, width: Int): String = {
    val s = value.toString
    if (s.length >= width) s else "0" * (width - s.length) + s
  }

  private def digitsToString(digits: Array[Int]): String = {
    val sb = new java.lang.StringBuilder(digits.length)
    var i  = 0
    while (i < digits.length) {
      sb.append(('0' + digits(i)).toChar)
      i += 1
    }
    sb.toString
  }

  private def getRandomElement(items: List[Int], intLength: Int): Int = items match {
    case Nil => randomValue(intLength)
    case _   => items(randomValue(items.length))
  }

  private def getRandomElement(items: List[String], stringLength: Int): String = items match {
    case Nil => lettersString(stringLength)
    case _   => items(randomValue(items.length))
  }

  def pan(bins: String*): String = {
    val idNum: String = digitString(9)
    val body: String  = bins.toList match {
      case Nil => s"${digitString(6)}$idNum"
      case bs  => s"${getRandomElement(bs, 6)}$idNum"
    }

    // Luhn: double every second digit from the left of the body; the check digit makes the total ≡ 0 (mod 10).
    var sum        = 0
    var i          = 0
    while (i < body.length) {
      val d = body.charAt(i).asDigit
      sum += (if (i % 2 == 0) { val doubled = d * 2; if (doubled > 9) doubled - 9 else doubled }
              else d)
      i += 1
    }
    val controlNum = (10 - sum % 10) % 10
    s"$body$controlNum"
  }

  def ogrn(): String = {
    val indicatorOGRN: Int   = getRandomElement(List(1, 5), 1)
    val year: String         = zeroPad(randomValue(2, 21), 2)
    val ruSubjectNum: String = zeroPad(randomValue(1, 90), 2)
    val idNum: String        = digitString(7)
    val result: String       = s"$indicatorOGRN$year$ruSubjectNum$idNum"
    val rem: Long            = result.toLong % 11

    if (rem == 10) s"${result}0" else s"$result$rem"
  }

  def ogrnip(): String = {
    val indicatorPSRNSP: Int = 3
    val year: String         = zeroPad(randomValue(2, 21), 2)
    val ruSubjectNum: String = zeroPad(randomValue(1, 90), 2)
    val idNum: String        = digitString(9)
    val result: String       = s"$indicatorPSRNSP$year$ruSubjectNum$idNum"
    // (first 14 digits mod 13) mod 10 — always 0..9, so no mod-11-style "== 10" special case is needed.
    val rem: Long            = result.toLong % 13 % 10

    s"$result$rem"
  }

  def kpp(): String = {
    val revenueServiceCode: String = zeroPad(randomValue(1, 10000), 4)
    val reasonForReg: String       = zeroPad(randomValue(1, 100), 2)
    val idNum: String              = zeroPad(randomValue(1, 1000), 3)

    s"$revenueServiceCode$reasonForReg$idNum"
  }

  /** Natural-person ITN (ИНН физлица) = 12 digits with TWO control digits. */
  def natITN(): String = {
    val d    = new Array[Int](12)
    var i    = 0
    while (i < 10) {
      d(i) = randomValue(0, 10)
      i += 1
    }
    var sum1 = 0
    i = 0
    while (i < 10) {
      sum1 += d(i) * NatInnFactors1(i)
      i += 1
    }
    d(10) = sum1 % 11 % 10
    var sum2 = 0
    i = 0
    while (i < 11) {
      sum2 += d(i) * NatInnFactors2(i)
      i += 1
    }
    d(11) = sum2 % 11 % 10
    digitsToString(d)
  }

  /** Legal-entity ITN (ИНН юрлица) = 10 digits with ONE control digit. */
  def jurITN(): String = {
    val d   = new Array[Int](10)
    var i   = 0
    while (i < 9) {
      d(i) = randomValue(0, 10)
      i += 1
    }
    var sum = 0
    i = 0
    while (i < 9) {
      sum += d(i) * JurInnFactors(i)
      i += 1
    }
    d(9) = sum % 11 % 10
    digitsToString(d)
  }

  def snils(): String = {
    val d       = new Array[Int](9)
    var i       = 0
    while (i < 9) {
      d(i) = randomValue(0, 10)
      i += 1
    }
    var sum     = 0
    i = 0
    while (i < 9) {
      sum += d(i) * (9 - i) // first digit ×9 .. ninth digit ×1
      i += 1
    }
    // control = sum mod 101; 100 is written as "00" (101 cannot occur after the mod).
    val control = sum % 101 match {
      case 100 => 0
      case c   => c
    }
    s"${digitsToString(d)}${zeroPad(control, 2)}"
  }

  def rusPassport(): String = {
    val ruSubjectNum: String = zeroPad(randomValue(1, 90), 2)
    val year: String         = zeroPad(randomValue(0, 21), 2)
    val idNum: String        = digitString(6)

    s"$ruSubjectNum$year$idNum"
  }
}
