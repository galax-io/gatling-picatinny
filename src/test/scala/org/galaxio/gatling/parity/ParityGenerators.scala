package org.galaxio.gatling.parity

import org.scalacheck.Gen

/** Inputs for the live-vs-frozen equivalence suites.
  *
  * Two things here are ENUMERATED rather than sampled, and both were learned the hard way:
  *
  *   - '''the escape sets'''. The #126 fast path is guarded by a predicate that must agree exactly with the branch list it
  *     short-circuits. Sampling can miss the single character where the two disagree, which is precisely the defect that would
  *     ship silently.
  *   - '''the Turkish letters in the cookie attribute alphabet'''. Three separate 20,000-input corpora reported ZERO
  *     differences for behaviour-changing parser variants until `ı` and `İ` entered the alphabet. An ASCII-only generator
  *     cannot see the defect class this suite exists for.
  */
object ParityGenerators {

  /** Every character `escapeJson` branches on: the two literal escapes, the five short control escapes, and representatives of
    * the `\\uXXXX` fallback at both ends of the C0 range and in the middle.
    */
  val JsonEscapeSet: List[Char] =
    List('"', '\\', '\n', '\r', '\t', '\b', '\f') ++ List(0, 1, 11, 14, 30, 31).map(_.toChar)

  /** Every character `escapeXml` branches on. Deliberately a DIFFERENT set: XML leaves control characters alone, so a rewrite
    * that unified the two escapers would diverge here.
    */
  val XmlEscapeSet: List[Char] = List('&', '<', '>', '"', '\'')

  /** Characters that must pass through untouched by both, including a surrogate pair (which the per-code-unit loop walks one
    * half at a time) and a lone high surrogate.
    */
  val PassThrough: List[Char] =
    List('a', 'Z', '0', ' ', '/', '~', 'é', 'ß', 'я', '中', '\uD83D', '\uDE00')

  private val interestingChars: Gen[Char] =
    Gen.oneOf(JsonEscapeSet ++ XmlEscapeSet ++ PassThrough)

  /** Strings biased hard towards the interesting characters — random text would almost never produce an escape.
    */
  val escapableString: Gen[String] =
    Gen.choose(0, 24).flatMap(n => Gen.listOfN(n, interestingChars).map(_.mkString))

  /** Field names go through the SAME escaper as values, on every branch, which is the half a value-only rewrite silently
    * misses.
    */
  val fieldName: Gen[String] =
    Gen.oneOf(
      Gen.alphaLowerStr.suchThat(_.nonEmpty),
      escapableString.suchThat(_.nonEmpty),
    )

  /** The enumerated boundary table — cases a generator reaches only by luck. */
  val boundaryStrings: List[String] = {
    val everyJson = JsonEscapeSet.mkString
    val everyXml  = XmlEscapeSet.mkString
    List(
      "",                        // empty
      " ",                       // single space
      everyJson,                 // entirely escapable (JSON)
      everyXml,                  // entirely escapable (XML)
      "a" * 64 + '"',            // only the LAST char escapes — the prefix-copy case
      "\"" + "a" * 64,           // only the FIRST char escapes
      "a" * 200,                 // long, nothing to escape — the fast path
      "😀",                      // a valid surrogate PAIR
      "\uD83D",                  // a LONE high surrogate — must pass through unchanged
      s"before${0.toChar}after", // NUL in the middle (built, not written literally)
      "tab\there",
    ) ++ JsonEscapeSet.map(c => s"x${c}y") ++ XmlEscapeSet.map(c => s"x${c}y")
  }

  // --- cookies ---------------------------------------------------------------------------------

  /** Attribute names in every case, INCLUDING the two Turkish letters. `DOMAİN` is the case that exposed a divergence three
    * ASCII-only corpora could not see.
    */
  private val attributeNames: Gen[String] =
    Gen.oneOf(
      "Domain",
      "DOMAIN",
      "domain",
      "DoMaIn",
      "DOMAİN",
      "DOMAıN",
      "Path",
      "PATH",
      "Max-Age",
      "MAX-AGE",
      "Secure",
      "SECURE",
      "HttpOnly",
      "HTTPONLY",
      "SameSite",
      "Unknown",
      "",
    )

  private val attributeValues: Gen[String] =
    Gen.oneOf("", "/", "/api", ".example.com", "3600", "-1", "abc", "99999999999999999999", " spaced ")

  private val attribute: Gen[String] =
    for {
      n <- attributeNames
      v <- Gen.option(attributeValues)
    } yield v.fold(n)(value => s"$n=$value")

  private val nameValue: Gen[String] =
    Gen.oneOf("sid=abc", "sid=", "=abc", "sid", "t=a=b=c", "  sid  =  abc  ", "")

  private val cookieLine: Gen[String] =
    for {
      nv    <- nameValue
      attrs <- Gen.choose(0, 5).flatMap(n => Gen.listOfN(n, attribute))
    } yield (nv :: attrs).mkString("; ")

  val rawSetCookie: Gen[String] =
    Gen.choose(1, 4).flatMap(n => Gen.listOfN(n, cookieLine)).map(_.mkString("\n"))

  /** Enumerated cookie shapes, each one a documented contract point from
    * `specs/014-perf-templates-cookies/contracts/cookie-parsing.md`.
    */
  val boundaryCookies: List[String] = List(
    "",                                    // empty input
    "\n",                                  // only a newline
    "a=1\n\nb=2",                          // blank line skipped
    "a=1;",                                // trailing separator
    "a=1;;",                               // repeated trailing separators
    "a=1;;b",                              // empty attribute in the middle
    ";",                                   // separator ONLY — raises today
    "malformed",                           // no `=` at all
    "a=1\r\nb=2",                          // CRLF — the \r is absorbed by trim
    "token=abc=def=ghi; Path=/",           // value containing the separator
    "sid=x; Domain=",                      // empty-valued domain != absent
    "sid=x; Domain=a.com; Domain=b.com",   // duplicate: last wins
    "sid=x; Secure=false",                 // flag spelled with a value: still set
    "sid=x; Secure; Secure",               // flag repeated
    "sid=x; Max-Age=1; Max-Age=nope",      // duplicate lifetime, last unparseable
    "sid=x; Max-Age=nope; Max-Age=2",      // duplicate lifetime, first unparseable
    "sid=x; Max-Age=",                     // empty lifetime
    "sid=x; Max-Age=99999999999999999999", // overflowing lifetime
    "sid=x; Max-Age=-1",                   // negative lifetime, accepted
    "sid=x; DOMAIN=up.example",            // ASCII capitals
    "sid=x; DOMAİN=dotted.example",        // the deliberate narrowing case
  )
}
