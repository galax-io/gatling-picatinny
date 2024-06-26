package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Feeder
import org.galaxio.gatling.utils.RandomPhoneGenerator
import org.galaxio.gatling.utils.phone.TypePhone.TypePhone
import org.galaxio.gatling.utils.phone.{PhoneFormat, TypePhone}

object RandomPhoneFeeder {

  def apply(
      paramName: String,
  ): Feeder[String] =
    feeder[String](paramName)(
      RandomPhoneGenerator.randomPhone(Seq.empty[PhoneFormat], TypePhone.E164PhoneNumber),
    )

  def apply(
      paramName: String,
      formats: PhoneFormat*,
  ): Feeder[String] =
    feeder[String](paramName)(
      RandomPhoneGenerator.randomPhone(formats, TypePhone.PhoneNumber),
    )

  def apply(
      paramName: String,
      typePhone: TypePhone,
      formats: PhoneFormat*,
  ): Feeder[String] =
    feeder[String](paramName)(
      RandomPhoneGenerator.randomPhone(formats, typePhone),
    )

  def apply(
      paramName: String,
      formatsPath: String,
      typePhone: TypePhone,
  ): Feeder[String] =
    feeder[String](paramName)(
      RandomPhoneGenerator.randomPhone(formatsPath, typePhone),
    )

  def apply(
      paramName: String,
      formatsPath: String,
  ): Feeder[String] =
    feeder[String](paramName)(
      RandomPhoneGenerator.randomPhone(formatsPath, TypePhone.PhoneNumber),
    )

}
