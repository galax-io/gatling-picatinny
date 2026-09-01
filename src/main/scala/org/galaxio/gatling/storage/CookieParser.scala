package org.galaxio.gatling.storage

import com.typesafe.scalalogging.StrictLogging

import java.util.Locale

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
    for {
      parts    <- Option.when(trimmed.nonEmpty)(trimmed.split(";").map(_.trim))
      nameValue = parts.head.split("=", 2)
      if nameValue.length >= 2
    } yield {
      val attrs = parts.tail.map { attr =>
        val kv = attr.split("=", 2)
        // `Locale.ROOT`, never the platform default (#333). RFC 6265 attribute names are
        // case-insensitive ASCII, so a server may send `DOMAIN=`. Under a Turkish/Azeri default
        // locale an ASCII capital `I` lowers to a dotless `ı`, so `DOMAIN` missed the `domain`
        // lookup below and the cookie silently took `defaultDomain` — wrong domain, no warning.
        // `domain` is the only recognised name containing an `I`, so it was the only reachable case.
        kv(0).trim.toLowerCase(Locale.ROOT) -> kv.lift(1).map(_.trim).getOrElse("")
      }.toMap

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
      )
    }
  }

}
