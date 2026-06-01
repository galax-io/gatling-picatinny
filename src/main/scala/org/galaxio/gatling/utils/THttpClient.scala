package org.galaxio.gatling.utils

import java.net.URI
import java.net.http.HttpClient.Redirect
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** Lightweight JDK HttpClient wrapper used by feeders and utility clients.
  *
  * @param followRedirects
  *   JDK redirect policy name
  * @param connectTimeoutInSeconds
  *   connection timeout in seconds
  */
case class THttpClient(followRedirects: String = "NEVER", connectTimeoutInSeconds: Long = 3) {

  private val jsonContentType: String = "application/json"
  private val client: HttpClient      = buildClient()

  def buildClient(): HttpClient = {
    val client: HttpClient = HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(connectTimeoutInSeconds))
      .followRedirects(Redirect.valueOf(followRedirects))
      .build()

    client
  }

  def GET(uri: String, headers: Seq[String] = Seq.empty[String]): HttpResponse[String] = {
    val builder = HttpRequest.newBuilder().uri(URI.create(uri))
    if (headers.nonEmpty) builder.headers(headers: _*)
    client.send(builder.build(), HttpResponse.BodyHandlers.ofString)
  }

  def POSTJson(uri: String, json: String, headers: Seq[String] = Seq.empty[String]): HttpResponse[String] = {
    val hdrs: Seq[String] = Seq("Content-Type", s"""$jsonContentType""") ++ headers

    val request: HttpRequest = HttpRequest
      .newBuilder()
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .uri(URI.create(uri))
      .headers(hdrs: _*)
      .build()

    client.send(request, HttpResponse.BodyHandlers.ofString)
  }

}
