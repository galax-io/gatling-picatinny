package org.galaxio.gatling.templates

import org.galaxio.gatling.jmh.JmhBenchmark
import org.galaxio.gatling.templates.Syntax._
import org.openjdk.jmh.annotations.Benchmark

class SyntaxBenchmark extends JmhBenchmark {

  private val flatFields: List[Field] = List(
    "id" - 42,
    "name" - "John",
    "email" - "john@example.com",
    "active" - true,
    "score" - 99.5,
  )

  private val nestedFields: List[Field] = List(
    "user" - (
      "id" - 1,
      "profile" - (
        "name" - "John",
        "address" - (
          "city" - "Moscow",
          "zip" - "101000",
        ),
      ),
    ),
  )

  private val largeArrayFields: List[Field] = List(
    "items" > (1 to 100: _*),
  )

  private val mixedFields: List[Field] = List(
    "id" - 1,
    "name" ~ "userName",
    "tags" > ("alpha", "beta", "#{gamma}"),
    "nested" - (
      "key" - "value",
      "count" - 42,
    ),
    "active" - true,
  )

  private val interpolateFields: List[Field] = List(
    "userId" ~ "uid",
    "sessionId" ~ "sid",
    "token" ~ "authToken",
    "requestId" ~ "reqId",
    "timestamp" ~ "ts",
  )

  // --- Escaping-heavy fixtures (#143) ---------------------------------------------------------
  // Every fixture ABOVE is escape-free: not one of their strings contains a character `escapeJson`
  // or `escapeXml` branches on. That makes the suite blind to the thing #126 changes — a rewrite
  // that fast-paths escape-free text would post a large win here while corrupting or slowing the
  // escaping path invisibly. These fixtures close that hole; the escape-free ones stay, because the
  // fast path needs measuring too.

  /** A C0 control character built numerically. Writing one as a literal here would embed a raw control byte in the source file;
    * building it keeps the source printable.
    */
  private def Ctrl(code: Int): Char = code.toChar

  /** Drives every JSON branch: the two literal escapes, the five short control escapes, and the `\\uXXXX` fallback at the low,
    * high and middle of the C0 range — the expensive path, which today builds each escape through general-purpose text
    * formatting.
    */
  private val escapeHeavyJsonFields: List[Field] = List(
    "quote\"and\\slash" - "he said \"hi\" \\ then left",
    "whitespace" - "line\nreturn\rtab\tback\bform\f",
    "controls" - s"${Ctrl(1)} low ${Ctrl(31)} high ${Ctrl(14)} mid",
    "mixed" - "{\"nested\":\"json\"}\n\tpadded",
  )

  /** Drives every XML branch. Note the sets differ: XML escapes `&<>"'` and leaves control characters alone, so a rewrite that
    * unified the two escapers would diverge here.
    */
  private val escapeHeavyXmlFields: List[Field] = List(
    "amp&name" - "a & b < c > d",
    "quotes" - "he said \"hi\" and 'bye'",
    "markup" - "<tag attr=\"v\">text</tag>",
    "entityish" - "&amp; &lt; already-looking",
  )

  /** The only escapable character is the LAST one, so the whole prefix is copied in bulk before the per-character loop starts —
    * the case a prefix-copy fast path is built for.
    */
  private val lateEscapeFields: List[Field] = List(
    "trailing" - ("a" * 128 + "\""),
    "clean" - ("b" * 128),
  )

  @Benchmark
  def makeJsonFlat(): String = makeJson(flatFields)

  @Benchmark
  def makeJsonNested(): String = makeJson(nestedFields)

  @Benchmark
  def makeJsonLargeArray(): String = makeJson(largeArrayFields)

  @Benchmark
  def makeJsonMixed(): String = makeJson(mixedFields)

  @Benchmark
  def makeJsonInterpolate(): String = makeJson(interpolateFields)

  @Benchmark
  def makeXmlFlat(): String = makeXml(flatFields)

  @Benchmark
  def makeXmlNested(): String = makeXml(nestedFields)

  @Benchmark
  def makeXmlLargeArray(): String = makeXml(largeArrayFields)

  @Benchmark
  def makeXmlMixed(): String = makeXml(mixedFields)

  @Benchmark
  def makeXmlInterpolate(): String = makeXml(interpolateFields)

  // --- Escaping-heavy paths (#143), the ones #126's before/after pair is judged on -------------

  @Benchmark
  def makeJsonEscapeHeavy(): String = makeJson(escapeHeavyJsonFields)

  @Benchmark
  def makeXmlEscapeHeavy(): String = makeXml(escapeHeavyXmlFields)

  /** Escape-free body, measured deliberately: this is the dominant real case and the one a no-escape fast path is built to make
    * free.
    */
  @Benchmark
  def makeJsonEscapeFree(): String = makeJson(flatFields)

  @Benchmark
  def makeJsonLateEscape(): String = makeJson(lateEscapeFields)
}
