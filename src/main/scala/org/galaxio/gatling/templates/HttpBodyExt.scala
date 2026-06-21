package org.galaxio.gatling.templates

import io.gatling.core.Predef._
import io.gatling.core.session.el._
import io.gatling.http.request.builder.HttpRequestBuilder
import org.galaxio.gatling.templates.Syntax._

/** Extension methods for `io.gatling.http.request.builder.HttpRequestBuilder` that add `jsonBody` and `xmlBody` DSL support.
  *
  * {{{
  * import org.galaxio.gatling.templates.HttpBodyExt._
  * import org.galaxio.gatling.templates.Syntax._
  *
  * http("PostData")
  *   .post(url)
  *   .jsonBody(
  *     "id" - 23,
  *     "name",                       // session variable #{name}
  *     "project" - (
  *       "id" ~ "projectId",         // session variable #{projectId}
  *       "name" - "Super Project",
  *       "sub" > (1, 2, 3, 4, 5, 6),
  *     ),
  *   )
  * }}}
  */
object HttpBodyExt {

  /** Enriches `HttpRequestBuilder` with template body methods.
    *
    * @param httpRequestBuilder
    *   the builder to extend
    */
  implicit class BodyOps(val httpRequestBuilder: HttpRequestBuilder) extends AnyVal {

    /** Sets a raw string as the request body with EL interpolation. */
    def body(string: String): HttpRequestBuilder = httpRequestBuilder.body(StringBody(string.el[String]))

    /** Builds a JSON request body from DSL fields and sets `Content-Type: application/json`.
      *
      * @param fs
      *   fields defined using the [[Syntax]] DSL
      */
    def jsonBody(fs: Field*): HttpRequestBuilder =
      httpRequestBuilder
        .body(
          StringBody(makeJson(fs: _*).el[String]),
        )
        .asJson

    /** Builds an XML request body from DSL fields and sets `Content-Type: application/xml`.
      *
      * @param fs
      *   fields defined using the [[Syntax]] DSL
      */
    def xmlBody(fs: Field*): HttpRequestBuilder =
      httpRequestBuilder
        .body(
          StringBody(makeXml(fs: _*).el[String]),
        )
        .asXml
  }
}
