package org.galaxio.gatling.feeders.faker

import org.galaxio.gatling.utils.phone.PhoneFormat

/** Predefined faker datasets used by [[Faker]].
  *
  * These values are public on purpose: projects can reuse the same catalogs for custom generators, assertions, or
  * scenario-specific filtering.
  */
object FakerData {

  /** Common male first names from several supported locales. */
  val maleFirstNames: Vector[String] = Vector(
    "Ivan",
    "Alexey",
    "Dmitry",
    "Sergey",
    "Nicolas",
    "John",
    "Pedro",
    "Lucas",
    "Martin",
    "Andres",
    "Mikhail",
    "Pavel",
    "Roman",
    "Ilya",
    "Maxim",
    "Daniel",
    "Michael",
    "Robert",
    "William",
    "Thomas",
    "Mateo",
    "Santiago",
    "Diego",
    "Gabriel",
    "Rafael",
  )

  /** Common female first names from several supported locales. */
  val femaleFirstNames: Vector[String] = Vector(
    "Anna",
    "Maria",
    "Elena",
    "Sofia",
    "Camila",
    "Julia",
    "Lucia",
    "Valentina",
    "Fernanda",
    "Olga",
    "Daria",
    "Natalia",
    "Ekaterina",
    "Irina",
    "Polina",
    "Emma",
    "Olivia",
    "Sophia",
    "Isabella",
    "Mia",
    "Martina",
    "Catalina",
    "Paula",
    "Agustina",
    "Beatriz",
  )

  /** Common last names for person and account payloads. */
  val lastNames: Vector[String] = Vector(
    "Ivanov",
    "Petrov",
    "Sidorov",
    "Smirnov",
    "Volkov",
    "Kuznetsov",
    "Garcia",
    "Silva",
    "Smith",
    "Brown",
    "Martinez",
    "Fernandez",
    "Rodriguez",
    "Lopez",
    "Gonzalez",
    "Santos",
    "Pereira",
    "Costa",
    "Muller",
    "Schmidt",
    "Dubois",
    "Martin",
    "Rossi",
    "Bianchi",
  )

  /** Person name prefixes suitable for synthetic profile data. */
  val personPrefixes: Vector[String] =
    Vector("Mr.", "Mrs.", "Ms.", "Miss", "Dr.", "Prof.")

  /** Job titles commonly useful in QA, engineering, and business scenarios. */
  val jobTitles: Vector[String] = Vector(
    "Performance Engineer",
    "QA Engineer",
    "Backend Developer",
    "Frontend Developer",
    "Full Stack Developer",
    "SRE",
    "DevOps Engineer",
    "Platform Engineer",
    "Product Analyst",
    "Data Engineer",
    "Security Engineer",
    "Mobile Engineer",
    "Solution Architect",
    "Technical Lead",
    "Product Manager",
    "Business Analyst",
  )

  /** Safe example-like domains for generated email and internet fields. */
  val domains: Vector[String] = Vector(
    "example.com",
    "test.local",
    "load.test",
    "picatinny.dev",
    "performance.test",
    "qa.example",
    "sandbox.internal",
    "demo.service",
    "api.test",
    "mail.test",
  )

  /** Browser, mobile, and client user-agent values for request headers. */
  val userAgents: Vector[String] = Vector(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 Version/17.4 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36",
    "Gatling/3.11.5",
    "Apache-HttpClient/5.3",
    "okhttp/4.12.0",
  )

  /** City names grouped by supported country. */
  val citiesByCountry: Map[Country, Vector[String]] = Map(
    Country.RU -> Vector(
      "Moscow",
      "Saint Petersburg",
      "Kazan",
      "Novosibirsk",
      "Yekaterinburg",
      "Nizhny Novgorod",
      "Samara",
      "Rostov-on-Don",
    ),
    Country.AR -> Vector("Buenos Aires", "Cordoba", "Rosario", "Mendoza", "La Plata", "Mar del Plata", "Salta", "Santa Fe"),
    Country.BR -> Vector(
      "Sao Paulo",
      "Rio de Janeiro",
      "Brasilia",
      "Curitiba",
      "Salvador",
      "Fortaleza",
      "Belo Horizonte",
      "Recife",
    ),
    Country.US -> Vector("New York", "Austin", "Seattle", "Chicago", "San Francisco", "Boston", "Denver", "Miami"),
    Country.GB -> Vector("London", "Manchester", "Birmingham", "Leeds", "Glasgow", "Liverpool", "Bristol", "Edinburgh"),
    Country.DE -> Vector("Berlin", "Munich", "Hamburg", "Frankfurt", "Cologne", "Stuttgart", "Dusseldorf", "Leipzig"),
    Country.FR -> Vector("Paris", "Lyon", "Marseille", "Toulouse", "Nice", "Nantes", "Bordeaux", "Lille"),
    Country.ES -> Vector("Madrid", "Barcelona", "Valencia", "Seville", "Bilbao", "Malaga", "Zaragoza", "Murcia"),
    Country.IT -> Vector("Rome", "Milan", "Naples", "Turin", "Florence", "Bologna", "Genoa", "Venice"),
    Country.AE -> Vector("Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Ras Al Khaimah", "Fujairah", "Al Ain", "Umm Al Quwain"),
  )

  /** Generic street names for address generation. */
  val streetNames: Vector[String] = Vector(
    "Main Street",
    "Performance Avenue",
    "Load Test Road",
    "Central Boulevard",
    "Liberty Street",
    "Market Street",
    "River Road",
    "Oak Street",
    "Maple Avenue",
    "Industrial Way",
    "Technology Park",
    "Green Lane",
    "North Road",
    "South Street",
    "Sunset Boulevard",
  )

  /** ISO-like currency codes used by finance and commerce generators. */
  val currencies: Vector[String] =
    Vector("USD", "EUR", "RUB", "BRL", "ARS", "GBP", "AED", "CHF", "JPY", "CNY", "INR", "KZT")

  /** Product names for cart, order, and catalog payloads. */
  val products: Vector[String] = Vector(
    "Laptop",
    "Phone",
    "Tablet",
    "Monitor",
    "Keyboard",
    "Mouse",
    "Headphones",
    "Subscription",
    "Support package",
    "Gift card",
    "Cloud storage",
    "API package",
    "Analytics plan",
    "Training seat",
    "Service credit",
  )

  /** Commerce categories for product and reporting payloads. */
  val categories: Vector[String] = Vector(
    "electronics",
    "services",
    "finance",
    "books",
    "travel",
    "cloud",
    "analytics",
    "education",
    "security",
    "support",
  )

  /** Weather condition labels for telemetry-like payloads. */
  val weatherConditions: Vector[String] =
    Vector("clear", "partly cloudy", "cloudy", "rain", "heavy rain", "storm", "snow", "fog", "wind", "drizzle", "hail")

  /** Lorem ipsum vocabulary used by text generators. */
  val loremWords: Vector[String] = Vector(
    "lorem",
    "ipsum",
    "dolor",
    "sit",
    "amet",
    "consectetur",
    "adipiscing",
    "elit",
    "sed",
    "do",
    "eiusmod",
    "tempor",
    "incididunt",
    "ut",
    "labore",
    "et",
    "dolore",
    "magna",
    "aliqua",
    "enim",
    "minim",
    "veniam",
    "quis",
    "nostrud",
    "exercitation",
    "ullamco",
    "laboris",
    "nisi",
    "aliquip",
    "commodo",
    "consequat",
  )

  /** Phone formats grouped by country for locale-aware phone generation. */
  val phoneFormatsByCountry: Map[Country, Seq[PhoneFormat]] = Map(
    Country.RU -> Seq(PhoneFormat("+7", 10, Seq("903", "906", "908", "926", "999", "915", "916", "977"), "+X XXX XXX-XX-XX")),
    Country.AR -> Seq(PhoneFormat("+54", 10, Seq("11", "221", "351", "261", "341", "381"), "+XX XXX XXX-XXXX")),
    Country.BR -> Seq(PhoneFormat("+55", 11, Seq("11", "21", "31", "41", "51", "61"), "+XX XX XXXXX-XXXX")),
    Country.US -> Seq(PhoneFormat("+1", 10, Seq("201", "212", "305", "415", "646", "718", "917"), "+X XXX XXX-XXXX")),
    Country.GB -> Seq(PhoneFormat("+44", 10, Seq("7400", "7500", "7700", "7800", "7900"), "+XX XXXX XXXXXX")),
    Country.DE -> Seq(PhoneFormat("+49", 10, Seq("151", "152", "160", "170", "171", "172"), "+XX XXX XXXXXXX")),
    Country.FR -> Seq(PhoneFormat("+33", 9, Seq("6", "7"), "+XX X XX XX XX XX")),
    Country.ES -> Seq(PhoneFormat("+34", 9, Seq("6", "7"), "+XX XXX XXX XXX")),
    Country.IT -> Seq(PhoneFormat("+39", 10, Seq("320", "328", "333", "347", "349"), "+XX XXX XXX XXXX")),
    Country.AE -> Seq(PhoneFormat("+971", 9, Seq("50", "52", "54", "55", "56", "58"), "+XXX XX XXX XXXX")),
  )
}
