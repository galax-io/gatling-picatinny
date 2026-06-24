package org.galaxio.gatling.storage

import com.typesafe.scalalogging.StrictLogging

final case class ParsedCookie(
    name: String,
    value: String,
    domain: Option[String] = None,
    path: Option[String] = None,
    maxAge: Option[Long] = None,
    secure: Boolean = false,
    httpOnly: Boolean = false,
)

object CookieParser extends StrictLogging {

  def parse(rawSetCookie: String, defaultDomain: String): Seq[ParsedCookie] =
    rawSetCookie.split("\n").toSeq.flatMap(parseSingle(_, defaultDomain))

  private def parseSingle(line: String, defaultDomain: String): Option[ParsedCookie] = {
    val trimmed = line.trim
    if (trimmed.isEmpty) return None

    val parts     = trimmed.split(";").map(_.trim)
    val nameValue = parts.head.split("=", 2)
    if (nameValue.length < 2) return None

    val attrs = parts.tail.map { attr =>
      val kv = attr.split("=", 2)
      kv(0).trim.toLowerCase -> kv.lift(1).map(_.trim).getOrElse("")
    }.toMap

    Some(
      ParsedCookie(
        name = nameValue(0).trim,
        value = nameValue(1).trim,
        domain = attrs.get("domain").orElse(Some(defaultDomain)),
        path = attrs.get("path"),
        maxAge = attrs.get("max-age").flatMap { raw =>
          val parsed = raw.toLongOption
          if (parsed.isEmpty)
            logger.warn(s"Ignoring unparseable Max-Age value '$raw' for cookie '${nameValue(0).trim}' (expected a Long)")
          parsed
        },
        secure = attrs.contains("secure"),
        httpOnly = attrs.contains("httponly"),
      ),
    )
  }

}
