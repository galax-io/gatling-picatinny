package org.galaxio.gatling.feeders

import io.gatling.core.feeder.Record
import org.galaxio.gatling.utils.THttpClient
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.{DefaultFormats, Extraction, JValue}

import java.util.Objects.requireNonNull

object VaultFeeder {

  /** Retrieves secrets from a single Vault path as a one-record feeder. */
  def apply(
      vaultUrl: String,
      secretPath: String,
      roleId: String,
      secretId: String,
      keys: List[String],
  ): IndexedSeq[Record[String]] = {
    requireNonNull(keys, "Keys list must not be null")

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats

    val body = approleLoginBody(roleId, secretId)

    val vaultTokenResponse: String = THttpClient()
      .POSTJson(s"""$vaultUrl/v1/auth/approle/login""", body)
      .body()

    val vaultToken = extractClientToken(parse(vaultTokenResponse))

    val getHeaders: Seq[String]   = Seq("X-Vault-Token", s"""$vaultToken""")
    val vaultDataResponse: String = THttpClient()
      .GET(s"""$vaultUrl/v1/$secretPath""", getHeaders)
      .body()

    val vaultDataJson: JValue = parse(vaultDataResponse)
    val data: Record[String]  = (vaultDataJson \ "data").extract[Map[String, String]]

    IndexedSeq(filterRecord(data, keys))
  }

  /** Retrieves secrets from multiple Vault paths and merges them into a single record.
    *
    * Useful when test data is spread across several Vault secrets.
    */
  def fromPaths(
      vaultUrl: String,
      roleId: String,
      secretId: String,
      paths: List[(String, List[String])],
  ): IndexedSeq[Record[String]] = {
    requireNonNull(paths, "Paths list must not be null")
    val merged = paths.flatMap { case (secretPath, keys) =>
      apply(vaultUrl, secretPath, roleId, secretId, keys).flatMap(_.toSeq)
    }.toMap
    IndexedSeq(merged)
  }

  /** Retrieves secrets using Vault token authentication (no AppRole).
    *
    * Suitable for local development or CI environments where a token is already available.
    */
  def withToken(
      vaultUrl: String,
      secretPath: String,
      vaultToken: String,
      keys: List[String],
  ): IndexedSeq[Record[String]] = {
    requireNonNull(keys, "Keys list must not be null")

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats

    val getHeaders: Seq[String]   = Seq("X-Vault-Token", vaultToken)
    val vaultDataResponse: String = THttpClient()
      .GET(s"""$vaultUrl/v1/$secretPath""", getHeaders)
      .body()

    val vaultDataJson: JValue = parse(vaultDataResponse)
    val data: Record[String]  = (vaultDataJson \ "data").extract[Map[String, String]]

    IndexedSeq(filterRecord(data, keys))
  }

  private def filterRecord(data: Record[String], keys: List[String]): Record[String] = {
    val selectedKeys = keys.toSet
    data.view.filterKeys(selectedKeys.contains).toMap
  }

  private[feeders] def approleLoginBody(roleId: String, secretId: String)(implicit formats: DefaultFormats): String =
    compact(render(Extraction.decompose(Map("role_id" -> roleId, "secret_id" -> secretId))))

  private[feeders] def extractClientToken(vaultTokenJson: JValue)(implicit formats: DefaultFormats): String =
    (vaultTokenJson \ "auth" \ "client_token").extract[String]
}
