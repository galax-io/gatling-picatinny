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

/** Minimal read-only HTTP seam consumed by feeders/config loaders.
  *
  * Extracting this interface lets HTTP-emitting consumers (e.g. [[org.galaxio.gatling.feeders.HttpJsonFeeder]]) be unit-tested
  * by mocking the fetch boundary instead of standing up a real server. [[THttpClient]] is the production implementation; the
  * real end-to-end HTTP path is exercised by the Gatling e2e simulations in the example overlays.
  */
trait HttpGetter {
  def get(uri: String, headers: Seq[String]): HttpResult
}

final class THttpClient private (
    followRedirects: Redirect,
    timeout: Duration,
    transport: Option[HttpRequest => HttpResult],
) extends AutoCloseable with HttpGetter {

  private lazy val client: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(timeout)
      .followRedirects(followRedirects)
      .build()

  // The single point where a request actually leaves the JVM. In tests a stub
  // transport is injected so request assembly + response handling are unit-tested
  // without a real server; the real wire path is covered by the e2e simulations.
  private val send0: HttpRequest => HttpResult =
    transport.getOrElse { request =>
      val response = client.send(request, HttpResponse.BodyHandlers.ofString)
      HttpResult(response.statusCode(), response.body())
    }

  override def get(uri: String, headers: Seq[String] = Seq.empty): HttpResult =
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
    if (transport.isEmpty)
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

    send0(request)
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
      None,
    )

  /** Test-only constructor: drives request assembly and response handling through an injected transport instead of the real
    * network, so HTTP logic can be unit-tested with a ScalaMock function (no server, no Docker).
    */
  private[gatling] def withTransport(transport: HttpRequest => HttpResult): THttpClient =
    new THttpClient(Redirect.NEVER, Duration.ofSeconds(3), Some(transport))
}
