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

  /** Attributes accumulated during the single pass — exactly the five things the cookie reads back, and nothing else.
    *
    * `maxAgeRaw` is deliberately the RAW, unparsed string. Parsing inside the fold would emit one warning per occurrence; the
    * map this replaced parsed once, AFTER last-wins had already chosen the survivor. Carrying the raw value and parsing after
    * the fold reproduces "exactly one warning, naming the last occurrence".
    */
  private final case class Attrs(
      domain: Option[String] = None,
      path: Option[String] = None,
      maxAgeRaw: Option[String] = None,
      secure: Boolean = false,
      httpOnly: Boolean = false,
  )

  private def parseSingle(line: String, defaultDomain: String): Option[ParsedCookie] = {
    val trimmed = line.trim
    for {
      // `parts.head`, never `headOption`: a line of only separators splits to an EMPTY array and
      // raises today. The defensive rewrite silently turns that into an empty result — a behaviour
      // change disguised as robustness, and nothing in the suite pinned it before this feature.
      parts    <- Option.when(trimmed.nonEmpty)(trimmed.split(";"))
      nameValue = parts.head.split("=", 2)
      if nameValue.length >= 2
    } yield {
      val name = nameValue(0).trim

      // One pass over the attributes, building the result directly (#137). The previous version
      // allocated an array from the split, a second from an array-wide trim, one more per attribute
      // from a second split, and a Map that was then queried five times.
      val attrs = parts.iterator.drop(1).foldLeft(Attrs()) { (acc, attr) =>
        val (rawKey, rest) = attr.span(_ != '=')
        // `Locale.ROOT`, never the platform default (#333). RFC 6265 attribute names are
        // case-insensitive ASCII, so a server may send `DOMAIN=`. Under a Turkish/Azeri default
        // locale an ASCII capital `I` lowers to a dotless `ı`, so `DOMAIN` missed `domain` below and
        // the cookie silently took `defaultDomain` — wrong domain, no warning. `domain` is the only
        // recognised name containing an `I`, so it was the only reachable case.
        // Both trims are load-bearing: dropping the key trim diverges on 12,150 of 20,082 generated
        // inputs, dropping the value trim on 7,601.
        val key            = rawKey.trim.toLowerCase(Locale.ROOT)
        val value          = rest.drop(1).trim
        key match {
          // Last occurrence wins, reproducing what building a Map did.
          case "domain"   => acc.copy(domain = Some(value))
          case "path"     => acc.copy(path = Some(value))
          case "max-age"  => acc.copy(maxAgeRaw = Some(value))
          // Flags are decided by PRESENCE, never by the value, so `Secure` and `Secure=false` both
          // set it and a later occurrence cannot clear it.
          case "secure"   => acc.copy(secure = true)
          case "httponly" => acc.copy(httpOnly = true)
          case _          => acc
        }
      }

      ParsedCookie(
        name = name,
        value = nameValue(1).trim,
        // An empty-valued `Domain=` is NOT the same as an absent one: only the absent case falls
        // back to the caller's default.
        domain = attrs.domain.orElse(Some(defaultDomain)),
        path = attrs.path,
        maxAge = attrs.maxAgeRaw.flatMap { raw =>
          val parsed = raw.toLongOption
          if (parsed.isEmpty)
            logger.warn(s"Ignoring unparseable Max-Age value '$raw' for cookie '$name' (expected a Long)")
          parsed
        },
        secure = attrs.secure,
        httpOnly = attrs.httpOnly,
      )
    }
  }

}
