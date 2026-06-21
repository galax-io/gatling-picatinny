package org.galaxio.gatling.feeders

import scala.annotation.tailrec

/** Independent re-implementations of the official Russian gov-ID control-digit algorithms, used to prove the feeders generate
  * **honest** (checksum-valid) values — not just digit strings of the right length.
  */
object GovIdValidators {

  /** Natural-person ITN (ИНН физлица): 12 digits, two control digits. */
  def validNatInn(s: String): Boolean =
    s.length == 12 && s.forall(_.isDigit) && {
      val d   = s.map(_.asDigit)
      val w11 = List(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
      val w12 = List(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
      val c11 = (w11.zip(d.take(10)).map { case (a, b) => a * b }.sum % 11) % 10
      val c12 = (w12.zip(d.take(11)).map { case (a, b) => a * b }.sum % 11) % 10
      c11 == d(10) && c12 == d(11)
    }

  /** Legal-entity ITN (ИНН юрлица): 10 digits, one control digit. */
  def validJurInn(s: String): Boolean =
    s.length == 10 && s.forall(_.isDigit) && {
      val d = s.map(_.asDigit)
      val w = List(2, 4, 10, 3, 5, 9, 4, 6, 8)
      val c = (w.zip(d.take(9)).map { case (a, b) => a * b }.sum % 11) % 10
      c == d(9)
    }

  /** SNILS: 11 digits; the last two are the control number over the first nine (weights 9..1). */
  def validSnils(s: String): Boolean =
    s.length == 11 && s.forall(_.isDigit) && {
      val sum = s.take(9).map(_.asDigit).zipWithIndex.map { case (dig, i) => dig * (9 - i) }.sum

      @tailrec def control(cs: Int): Int = cs match {
        case x if x >= 10 && x < 100 => x
        case x if x < 10             => x
        case 100 | 101               => 0
        case _                       => control(cs % 101)
      }

      control(sum) == s.substring(9, 11).toInt
    }
}
