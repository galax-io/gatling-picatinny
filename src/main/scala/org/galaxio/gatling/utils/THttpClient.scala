package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** Lightweight JDK HttpClient wrapper used by feeders and utility clients.
  *
  * This client is intended for feeder setup and configuration fetching, not for the Gatling virtual-user hot path.
  *
  * @param followRedirects
  *   JDK redirect policy name
  * @param connectTimeoutInSeconds
  *   connection timeout in seconds; must be &gt; 0
  * @param requestTimeoutInSeconds
  *   per-request read timeout in seconds (0 = no timeout); must be &ge; 0
  */
case class THttpClient(
    followRedirects: String = "NEVER",
    connectTimeoutInSeconds: Long = 3,
    requestTimeoutInSeconds: Long = 30,
) {
  require(connectTimeoutInSeconds > 0, s"connectTimeoutInSeconds must be > 0, got $connectTimeoutInSeconds")
  require(requestTimeoutInSeconds >= 0, s"requestTimeoutInSeconds must be >= 0, got $requestTimeoutInSeconds")

  private val jsonContentType: String = "application/json"

  private val client: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(connectTimeoutInSeconds))
      .followRedirects(Redirect.valueOf(followRedirects))
      .build()

  private def applyTimeout(builder: HttpRequest.Builder): HttpRequest.Builder =
    if (requestTimeoutInSeconds > 0) builder.timeout(Duration.ofSeconds(requestTimeoutInSeconds))
    else builder

  def GET(uri: String, headers: Seq[String] = Seq.empty): HttpResponse[String] = {
    val builder = applyTimeout(HttpRequest.newBuilder().uri(URI.create(uri)))
    if (headers.nonEmpty) builder.headers(headers: _*)
    client.send(builder.build(), HttpResponse.BodyHandlers.ofString)
  }

  def POSTJson(uri: String, json: String, headers: Seq[String] = Seq.empty): HttpResponse[String] = {
    val hdrs: Seq[String] = Seq("Content-Type", jsonContentType) ++ headers

    val builder = applyTimeout(
      HttpRequest
        .newBuilder()
        .uri(URI.create(uri))
        .POST(HttpRequest.BodyPublishers.ofString(json)),
    )
    builder.headers(hdrs: _*)
    client.send(builder.build(), HttpResponse.BodyHandlers.ofString)
  }
}
