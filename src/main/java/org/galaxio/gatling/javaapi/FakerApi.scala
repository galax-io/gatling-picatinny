package org.galaxio.gatling.javaapi

import java.{util => ju}
import java.time.{LocalDate, LocalDateTime}
import scala.jdk.CollectionConverters._
import org.galaxio.gatling.feeders.faker._

/** Java/Kotlin facade for the Faker data generation API.
  *
  * Scala users should use [[org.galaxio.gatling.feeders.faker.Faker]] directly.
  * This object bridges nested Scala objects into flat static methods accessible from Java and Kotlin.
  *
  * {{{
  * // Java
  * import static org.galaxio.gatling.javaapi.FakerApi.*;
  * import static org.galaxio.gatling.javaapi.Feeders.GeneratedFeeder;
  *
  * var users = GeneratedFeeder(
  *     field("email", email()),
  *     field("phone", phoneMobile(countryRU(), phoneFormatE164())),
  *     field("inn", innPerson())
  * );
  * }}}
  */
object FakerApi {

  // --- Field helper ---

  def field[A](name: String, generator: Generator[A]): Field[A] = Field(name, generator)

  // --- UUID ---

  def uuidString: Generator[String] = Faker.uuid.string
  def uuidValue: Generator[ju.UUID] = Faker.uuid.value

  // --- String ---

  def alphabetic(length: Int): Generator[String]   = Faker.string.alphabetic(length)
  def alphanumeric(length: Int): Generator[String]  = Faker.string.alphanumeric(length)
  def numeric(length: Int): Generator[String]       = Faker.string.numeric(length)
  def hex(length: Int): Generator[String]           = Faker.string.hex(length)
  def cyrillic(length: Int): Generator[String]      = Faker.string.cyrillic(length)

  // --- Number ---

  def intBetween(min: Int, max: Int): Generator[Int]             = Faker.number.int(min, max)
  def longBetween(min: Long, max: Long): Generator[Long]         = Faker.number.long(min, max)
  def doubleBetween(min: Double, max: Double): Generator[Double] = Faker.number.double(min, max)
  def booleanValue: Generator[Boolean]                           = Faker.number.boolean

  // --- Person ---

  def firstName(): Generator[String] = Faker.person.firstName()
  def lastName(): Generator[String]  = Faker.person.lastName()
  def fullName(): Generator[String]  = Faker.person.fullName()
  def jobTitle(): Generator[String]  = Faker.person.jobTitle()

  // --- Internet ---

  def email(): Generator[String]                = Faker.internet.email()
  def email(domain: String): Generator[String]  = Faker.internet.email(domain)
  def username(): Generator[String]             = Faker.internet.username()
  def url(): Generator[String]                  = Faker.internet.url()
  def password(length: Int): Generator[String]  = Faker.internet.password(length)
  def ipv4(): Generator[String]                 = Faker.internet.ipv4()
  def ipv6(): Generator[String]                 = Faker.internet.ipv6()

  // --- Phone ---

  def phoneMobile(country: Country, format: PhoneFormatMode): Generator[String] = Faker.phone.mobile(country, format)
  def phoneMobile(country: Country): Generator[String]                          = Faker.phone.mobile(country)
  def phoneTollFree(country: Country): Generator[String]                        = Faker.phone.tollFree(country)
  def phoneTollFree(): Generator[String]                                        = Faker.phone.tollFree()

  // --- Location ---

  def city(country: Country): Generator[String]       = Faker.location.city(country)
  def postalCode(country: Country): Generator[String] = Faker.location.postalCode(country)
  def latitude(): Generator[Double]                   = Faker.location.latitude()
  def longitude(): Generator[Double]                  = Faker.location.longitude()

  // --- Date ---

  def dateToday(): Generator[LocalDate]                                 = Faker.date.today()
  def dateNow(): Generator[LocalDateTime]                               = Faker.date.now()
  def datePast(days: Long): Generator[LocalDate]                        = Faker.date.past(days)
  def dateFuture(days: Long): Generator[LocalDate]                      = Faker.date.future(days)
  def dateBetween(from: LocalDate, to: LocalDate): Generator[LocalDate] = Faker.date.between(from, to)

  def formatDate(gen: Generator[LocalDate], pattern: String): Generator[String]         = Faker.date.formatDate(gen, pattern)
  def formatDateTime(gen: Generator[LocalDateTime], pattern: String): Generator[String] = Faker.date.formatDateTime(gen, pattern)

  // --- Finance ---

  def pan(): Generator[String]                                = Faker.finance.pan()
  def pan(bins: ju.List[String]): Generator[String]           = Faker.finance.pan(bins.asScala.toSeq: _*)
  def amount(min: Double, max: Double): Generator[BigDecimal] = Faker.finance.amount(BigDecimal(min), BigDecimal(max))
  def currency(): Generator[String]                           = Faker.finance.currency()
  def iban(country: Country): Generator[String]               = Faker.finance.iban(country)
  def transactionId(): Generator[String]                      = Faker.finance.transactionId()
  def bic(): Generator[String]                                = Faker.finance.bic()
  def accountNumber(length: Int): Generator[String]           = Faker.finance.accountNumber(length)

  // --- Commerce ---

  def productName(): Generator[String] = Faker.commerce.productName()
  def category(): Generator[String]    = Faker.commerce.category()
  def sku(): Generator[String]         = Faker.commerce.sku()
  def orderId(): Generator[String]     = Faker.commerce.orderId()

  // --- Russian IDs ---

  def innPerson(): Generator[String]  = Faker.ru.inn.person()
  def innCompany(): Generator[String] = Faker.ru.inn.company()
  def kpp(): Generator[String]        = Faker.ru.kpp()
  def ogrn(): Generator[String]       = Faker.ru.ogrn()
  def ogrnip(): Generator[String]     = Faker.ru.ogrnip()
  def snils(): Generator[String]      = Faker.ru.snils()

  // --- Brazilian ---

  def cpf(formatted: Boolean): Generator[String] = Faker.br.cpf(formatted)
  def cpf(): Generator[String]                   = Faker.br.cpf()

  // --- Argentine ---

  def dni(formatted: Boolean): Generator[String] = Faker.ar.dni(formatted)
  def dni(): Generator[String]                   = Faker.ar.dni()

  // --- European IDs ---

  def nif(): Generator[String]                         = Faker.es.nif()
  def codiceFiscale(): Generator[String]               = Faker.it.codiceFiscale()
  def steueridentifikationsnummer(): Generator[String] = Faker.de.steueridentifikationsnummer()
  def ssn(formatted: Boolean): Generator[String]       = Faker.us.ssn(formatted)
  def ssn(): Generator[String]                         = Faker.us.ssn()
  def nino(): Generator[String]                        = Faker.gb.nino()
  def nir(): Generator[String]                         = Faker.fr.nir()

  // --- Passport ---

  def passportRu(): Generator[String]                     = Faker.passport.ru()
  def passportNumber(country: Country): Generator[String] = Faker.passport.number(country)

  // --- Lorem ---

  def loremWord(): Generator[String]                    = Faker.lorem.word()
  def loremWords(count: Int): Generator[String]         = Faker.lorem.words(count)
  def loremSentence(wordsCount: Int): Generator[String] = Faker.lorem.sentence(wordsCount)

  // --- Utility ---

  def oneOfList[A](items: ju.List[A]): Generator[A] = Faker.oneOf(items.asScala.toSeq)

  // --- Country constants ---

  def countryRU: Country = Country.RU
  def countryAR: Country = Country.AR
  def countryBR: Country = Country.BR
  def countryUS: Country = Country.US
  def countryGB: Country = Country.GB
  def countryDE: Country = Country.DE
  def countryFR: Country = Country.FR
  def countryES: Country = Country.ES
  def countryIT: Country = Country.IT
  def countryAE: Country = Country.AE
  def countryJP: Country = Country.JP
  def countryCN: Country = Country.CN
  def countryIN: Country = Country.IN
  def countryCA: Country = Country.CA
  def countryAU: Country = Country.AU
  def countryMX: Country = Country.MX

  // --- PhoneFormatMode constants ---

  def phoneFormatE164: PhoneFormatMode          = PhoneFormatMode.E164
  def phoneFormatNational: PhoneFormatMode      = PhoneFormatMode.National
  def phoneFormatInternational: PhoneFormatMode = PhoneFormatMode.International
  def phoneFormatTollFree: PhoneFormatMode      = PhoneFormatMode.TollFree

  // --- Gender constants ---

  def genderMale: Gender       = Gender.Male
  def genderFemale: Gender     = Gender.Female
  def genderUnspecified: Gender = Gender.Unspecified
}
