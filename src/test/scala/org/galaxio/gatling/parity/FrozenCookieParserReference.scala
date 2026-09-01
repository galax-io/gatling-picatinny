package org.galaxio.gatling.parity

import com.typesafe.scalalogging.StrictLogging
import org.galaxio.gatling.storage.ParsedCookie

import java.util.Locale

/** A VERBATIM, FROZEN copy of the parse region of `storage/CookieParser.scala`.
  *
  * Same contract as [[FrozenSyntaxReference]]: do not edit the copied region.
  *
  * '''The package matters.''' This must NOT live in `org.galaxio.gatling.storage`: scala-logging derives the logger name from
  * the class, so an oracle there would log under a child of `org.galaxio.gatling.storage` and
  * `LogCapture.warns("org.galaxio.gatling.storage")` would capture the ORACLE's warnings alongside the live parser's — making
  * warning-parity vacuously true.
  *
  * Reproduce with:
  * {{{
  * git show 7b44616age/CookieParser.scala | sed -n '19,54p'
  * }}}
  *
  * Frozen at 7b44616 (2026-09-01), AFTER the `Locale.ROOT` fix, so live and frozen agree under every host locale — including
  * inside a `LocaleFixture` window. Freezing before it would have made the parity suite fail on exactly the Turkish inputs the
  * fix corrects.
  */
private[parity] object FrozenCookieParserReference extends StrictLogging {

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
