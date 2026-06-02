package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

case class HttpResult(statusCode: Int, body: String) {
  def isSuccess: Boolean = statusCode >= 200 && statusCode < 300
}

class HttpClientException(
    val method: String,
    val uri: String,
    val statusCode: Int,
    override val getMessage: String,
) extends RuntimeException(getMessage)

object HttpClientException {

  private val BodyPreviewLimit = 500

  def apply(method: String, uri: String, statusCode: Int, body: String): HttpClientException =
    new HttpClientException(
      method,
      uri,
      statusCode,
      s"HTTP $method $uri failed with status $statusCode: ${body.take(BodyPreviewLimit)}",
    )
}

/** Lightweight JDK HttpClient wrapper used by feeders and utility clients.
  *
  * @param followRedirects
  *   JDK redirect policy name
  * @param timeoutInSeconds
  *   connect and per-request timeout in seconds
  */
case class THttpClient(followRedirects: String = "NEVER", timeoutInSeconds: Long = 3) extends AutoCloseable {

  private val jsonContentType: String = "application/json"

  private val client: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(timeoutInSeconds))
      .followRedirects(Redirect.valueOf(followRedirects))
      .build()

  def get(uri: String, headers: Seq[String] = Seq.empty): HttpResult =
    execute("GET", uri, headers, None)

  def post(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    execute("POST", uri, headers, Some(body))

  def put(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    execute("PUT", uri, headers, Some(body))

  def getOrThrow(uri: String, headers: Seq[String] = Seq.empty): HttpResult =
    checked(get(uri, headers), "GET", uri)

  def postOrThrow(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    checked(post(uri, body, headers), "POST", uri)

  def putOrThrow(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    checked(put(uri, body, headers), "PUT", uri)

  override def close(): Unit =
    client.executor().ifPresent(_.asInstanceOf[java.util.concurrent.ExecutorService].shutdown())

  private def execute(method: String, uri: String, headers: Seq[String], body: Option[String]): HttpResult = {
    val builder = HttpRequest
      .newBuilder()
      .uri(URI.create(uri))
      .timeout(Duration.ofSeconds(timeoutInSeconds))

    val allHeaders = body match {
      case Some(_) => Seq("Content-Type", jsonContentType) ++ headers
      case None    => headers
    }
    if (allHeaders.nonEmpty) builder.headers(allHeaders: _*)

    val publisher = body match {
      case Some(json) => HttpRequest.BodyPublishers.ofString(json)
      case None       => HttpRequest.BodyPublishers.noBody()
    }
    builder.method(method, publisher)

    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString)
    HttpResult(response.statusCode(), response.body())
  }

  private def checked(result: HttpResult, method: String, uri: String): HttpResult =
    if (result.isSuccess) result
    else throw HttpClientException(method, uri, result.statusCode, result.body)
}
