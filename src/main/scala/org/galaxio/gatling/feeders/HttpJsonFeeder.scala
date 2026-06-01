package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Record
import org.galaxio.gatling.utils.THttpClient
import org.json4s.native.JsonMethods
import org.json4s.{DefaultFormats, Formats, JValue}

import java.util.Objects.requireNonNull

/** Creates a one-record feeder from a JSON HTTP endpoint.
  *
  * Use this for small configuration or test-data APIs. Non-string top-level fields (numbers, booleans) are converted
  * to their string representation; richer JSON transformations should be performed before values reach the feeder
  * boundary.
  */
object HttpJsonFeeder {

  private lazy val sharedClient: THttpClient = THttpClient()

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
    requireNonNull(keys, "Keys list must not be null")

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats

    val response = sharedClient.GET(url, headers).body()
    val json     = JsonMethods.parse(response)
    val data     = extractFields(json)
    val keySet   = keys.toSet

    IndexedSeq(data.view.filterKeys(keySet.contains).toMap)
  }

  private def extractFields(json: JValue)(implicit formats: Formats): Record[String] =
    json.extract[Map[String, Any]].view.mapValues {
      case null => "null"
      case v    => v.toString
    }.toMap
}
