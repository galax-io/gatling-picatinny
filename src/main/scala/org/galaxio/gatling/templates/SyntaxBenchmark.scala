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
}
