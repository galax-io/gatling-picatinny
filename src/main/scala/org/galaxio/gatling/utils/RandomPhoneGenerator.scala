package org.galaxio.gatling.utils

import org.galaxio.gatling.utils.phone.TypePhone._
import org.galaxio.gatling.utils.phone.{Phone, PhoneFormat}

object RandomPhoneGenerator {

  def randomPhone(
      formats: Seq[PhoneFormat],
      typePhone: TypePhone,
  ): String = typePhone match {
    case PhoneNumber         => Phone(formats).phoneNumber
    case TollFreePhoneNumber => Phone(formats).tollFreePhoneNumber
    case E164PhoneNumber     => Phone(formats).e164PhoneNumber
  }

  def randomPhone(
      pathToFormats: String,
      typePhone: TypePhone,
  ): String = typePhone match {
    case PhoneNumber         => Phone(pathToFormats).phoneNumber
    case TollFreePhoneNumber => Phone(pathToFormats).tollFreePhoneNumber
    case E164PhoneNumber     => Phone(pathToFormats).e164PhoneNumber
  }

}
