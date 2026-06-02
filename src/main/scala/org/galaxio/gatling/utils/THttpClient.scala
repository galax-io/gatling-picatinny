package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.ExecutorService

case class HttpResult(statusCode: Int, body: String) {
  def isSuccess: Boolean = statusCode >= 200 && statusCode < 300
}

class HttpClientException(
    val method: String,
    val uri: String,
    val statusCode: Int,
    message: String,
) extends RuntimeException(message)

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
final class THttpClient(val followRedirects: String = "NEVER", val timeoutInSeconds: Long = 3) extends AutoCloseable {

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

  // JDK 17 HttpClient has no public close(); shut down the executor if one was provided.
  // The internal SelectorManager daemon thread will terminate on JVM exit regardless.
  override def close(): Unit =
    client.executor().ifPresent {
      case es: ExecutorService => es.shutdown()
      case _                   => ()
    }

  private def execute(method: String, uri: String, headers: Seq[String], body: Option[String]): HttpResult = {
    val allHeaders = body.fold(headers)(_ => Seq("Content-Type", jsonContentType) ++ headers)
    val publisher  = body.fold(HttpRequest.BodyPublishers.noBody())(HttpRequest.BodyPublishers.ofString)

    val base    = HttpRequest.newBuilder().uri(URI.create(uri)).timeout(Duration.ofSeconds(timeoutInSeconds))
    val withHdr = if (allHeaders.nonEmpty) base.headers(allHeaders: _*) else base
    val request = withHdr.method(method, publisher).build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString)
    HttpResult(response.statusCode(), response.body())
  }

  private def checked(result: HttpResult, method: String, uri: String): HttpResult =
    if (result.isSuccess) result
    else throw HttpClientException(method, uri, result.statusCode, result.body)
}

object THttpClient {
  def apply(followRedirects: String = "NEVER", timeoutInSeconds: Long = 3): THttpClient =
    new THttpClient(followRedirects, timeoutInSeconds)
}
