package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** Lightweight JDK HttpClient wrapper used by feeders and utility clients.
  *
  * @param followRedirects
  *   JDK redirect policy name
  * @param timeoutInSeconds
  *   connect and per-request timeout in seconds
  */
case class THttpClient(followRedirects: String = "NEVER", timeoutInSeconds: Long = 3) {

  private val jsonContentType: String = "application/json"

  private val client: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(timeoutInSeconds))
      .followRedirects(Redirect.valueOf(followRedirects))
      .build()

  def get(uri: String, headers: Seq[String] = Seq.empty): HttpResponse[String] = {
    val builder = HttpRequest.newBuilder().uri(URI.create(uri)).timeout(Duration.ofSeconds(timeoutInSeconds))
    if (headers.nonEmpty) builder.headers(headers: _*)
    client.send(builder.build(), HttpResponse.BodyHandlers.ofString)
  }

  def post(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResponse[String] =
    sendJson(uri, body, "POST", headers)

  def put(uri: String, body: String, headers: Seq[String] = Seq.empty): HttpResponse[String] =
    sendJson(uri, body, "PUT", headers)

  private def sendJson(
      uri: String,
      json: String,
      method: String,
      headers: Seq[String],
  ): HttpResponse[String] = {
    val hdrs: Seq[String] = Seq("Content-Type", jsonContentType) ++ headers

    val request: HttpRequest = HttpRequest
      .newBuilder()
      .method(method, HttpRequest.BodyPublishers.ofString(json))
      .uri(URI.create(uri))
      .headers(hdrs: _*)
      .timeout(Duration.ofSeconds(timeoutInSeconds))
      .build()

    client.send(request, HttpResponse.BodyHandlers.ofString)
  }
}
