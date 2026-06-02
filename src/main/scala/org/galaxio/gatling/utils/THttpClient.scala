package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.concurrent.ExecutorService

sealed trait HttpMethod {
  def name: String
}

object HttpMethod {
  case object Get  extends HttpMethod { val name = "GET"  }
  case object Post extends HttpMethod { val name = "POST" }
  case object Put  extends HttpMethod { val name = "PUT"  }
}

case class HttpResult(statusCode: Int, body: String) {
  def isSuccess: Boolean = statusCode >= 200 && statusCode < 300
}

class HttpClientException(
    val method: HttpMethod,
    val uri: String,
    val statusCode: Int,
    message: String,
) extends RuntimeException(message)

object HttpClientException {

  private val BodyPreviewLimit = 500

  def apply(method: HttpMethod, uri: String, statusCode: Int, body: String): HttpClientException =
    new HttpClientException(
      method,
      uri,
      statusCode,
      s"HTTP ${method.name} $uri failed with status $statusCode: ${body.take(BodyPreviewLimit)}",
    )
}

final class THttpClient private (followRedirects: Redirect, timeout: Duration) extends AutoCloseable {

  private val client: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(timeout)
      .followRedirects(followRedirects)
      .build()

  def get(uri: String, headers: Seq[String] = Seq.empty): HttpResult =
    send(HttpMethod.Get, uri, headers)

  def post(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    send(HttpMethod.Post, uri, headers, Some(body))

  def put(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    send(HttpMethod.Put, uri, headers, Some(body))

  def getOrThrow(uri: String, headers: Seq[String] = Seq.empty): HttpResult =
    ensureSuccess(get(uri, headers), HttpMethod.Get, uri)

  def postOrThrow(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    ensureSuccess(post(uri, body, headers), HttpMethod.Post, uri)

  def putOrThrow(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResult =
    ensureSuccess(put(uri, body, headers), HttpMethod.Put, uri)

  // JDK 17 HttpClient has no public close(). Shut down the executor if present;
  // the internal SelectorManager daemon terminates on JVM exit regardless.
  override def close(): Unit =
    client.executor().ifPresent {
      case es: ExecutorService => es.shutdown()
      case _                   => ()
    }

  private def send(
      method: HttpMethod,
      uri: String,
      headers: Seq[String],
      body: Option[String] = None,
  ): HttpResult = {
    val allHeaders = body.fold(headers)(_ => "Content-Type" +: "application/json" +: headers)
    val publisher  = body.fold(HttpRequest.BodyPublishers.noBody())(HttpRequest.BodyPublishers.ofString)

    val base    = HttpRequest.newBuilder().uri(URI.create(uri)).timeout(timeout)
    val withHdr = if (allHeaders.nonEmpty) base.headers(allHeaders: _*) else base
    val request = withHdr.method(method.name, publisher).build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString)
    HttpResult(response.statusCode(), response.body())
  }

  private def ensureSuccess(result: HttpResult, method: HttpMethod, uri: String): HttpResult =
    if (result.isSuccess) result
    else throw HttpClientException(method, uri, result.statusCode, result.body)
}

object THttpClient {

  def apply(
      followRedirects: String = "NEVER",
      timeoutInSeconds: Long = 3,
  ): THttpClient =
    new THttpClient(
      Redirect.valueOf(followRedirects),
      Duration.ofSeconds(timeoutInSeconds),
    )
}
