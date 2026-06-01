package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Record
import org.galaxio.gatling.utils.THttpClient
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats, JValue}

/** Creates a one-record feeder from a JSON HTTP endpoint.
  *
  * Use this for small configuration or test-data APIs. Non-string top-level fields (numbers, booleans, null) are
  * converted to their string representation; richer JSON transformations should be performed before values reach the
  * feeder boundary.
  */
object HttpJsonFeeder {

  private lazy val sharedClient: THttpClient = THttpClient()
  private implicit val formats: Formats      = DefaultFormats

  /** Fetches JSON from an HTTP GET endpoint and extracts selected top-level fields into a feeder record.
    *
    * @param url
    *   full URL to fetch
    * @param keys
    *   top-level JSON field names to extract
    * @param headers
    *   optional HTTP headers as alternating `name, value, name, value, ...`
    */
  def apply(
      url: String,
      keys: List[String],
      headers: Seq[String] = Seq.empty,
  ): IndexedSeq[Record[String]] = {
    require(url.nonEmpty, "URL must be non-empty")
    require(keys != null, "Keys list must not be null")

    val response = sharedClient.GET(url, headers).body()
    val json     = JsonMethods.parse(response)
    val data     = extractFields(json)
    val keySet   = keys.toSet

    IndexedSeq(data.view.filterKeys(keySet.contains).toMap)
  }

  private def extractFields(json: JValue): Record[String] =
    json.extract[Map[String, Any]].view.mapValues {
      case null => "null"
      case v    => v.toString
    }.toMap
}
