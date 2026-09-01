package org.galaxio.gatling.storage

import org.galaxio.gatling.jmh.JmhBenchmark
import org.openjdk.jmh.annotations.Benchmark

/** Measures `CookieParser.parse` (#144), the path #137 rewrites.
  *
  * What drives the cost is the number of ATTRIBUTES per cookie, not the number of cookies: per line the parser allocates an
  * array from the `;` split, a second from the array-wide trim, one more per attribute from the `=` split, and a `Map` that is
  * then queried five times. So the fixtures below vary attribute density rather than only cookie count.
  *
  * `parse` is called once per `restoreCookies` step per virtual user, so this is a real per-invocation cost even though it is
  * not per-request.
  */
class CookieParserBenchmark extends JmhBenchmark {

  private val Domain = "example.com"

  /** The realistic shape: several cookies, each carrying the full attribute set a session cookie normally does.
    */
  private val fullAttributes: String =
    (1 to 10)
      .map(i => s"session_$i=value_$i; Path=/api/v$i; Domain=.example.com; Max-Age=3600; Secure; HttpOnly")
      .mkString("\n")

  /** Same cookie count, no attributes — isolates the name/value split from the attribute fold. */
  private val bareNameValue: String =
    (1 to 10).map(i => s"session_$i=value_$i").mkString("\n")

  /** One cookie, many attributes — the attribute fold with the per-attribute allocations dominant. */
  private val attributeDense: String =
    "sid=abc123; Path=/; Domain=.example.com; Max-Age=7200; Secure; HttpOnly; SameSite=Strict; Priority=High; Partitioned"

  /** Exercises the branches a naive rewrite gets wrong: duplicate attributes (last wins), a flag spelled with a value, an
    * empty-valued domain, and a value containing the separator.
    */
  private val awkward: String =
    "t=a=b=c; Domain=first.com; Domain=second.com; Secure=false; Max-Age=1; Max-Age=2; Path=/x"

  @Benchmark
  def parseFullAttributes(): Seq[ParsedCookie] = CookieParser.parse(fullAttributes, Domain)

  @Benchmark
  def parseBareNameValue(): Seq[ParsedCookie] = CookieParser.parse(bareNameValue, Domain)

  @Benchmark
  def parseAttributeDense(): Seq[ParsedCookie] = CookieParser.parse(attributeDense, Domain)

  @Benchmark
  def parseAwkward(): Seq[ParsedCookie] = CookieParser.parse(awkward, Domain)
}
