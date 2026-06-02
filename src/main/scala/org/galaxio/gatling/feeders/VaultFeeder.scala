package org.galaxio.gatling.feeders

import com.typesafe.scalalogging.LazyLogging
import io.gatling.core.feeder.Record
import org.galaxio.gatling.utils.THttpClient
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.{DefaultFormats, Extraction, JValue}

/** Strategy for handling duplicate keys when merging secrets from multiple Vault paths. */
sealed trait DuplicateKeyStrategy

object DuplicateKeyStrategy {

  /** Throw [[IllegalArgumentException]] listing the colliding keys. */
  case object FailOnDuplicate extends DuplicateKeyStrategy

  /** Keep the value from the last path in the list (last-writer-wins). Logs a warning. */
  case object LastWins extends DuplicateKeyStrategy

  /** Keep the value from the first path in the list. Logs a warning. */
  case object FirstWins extends DuplicateKeyStrategy
}

object VaultFeeder extends LazyLogging {

  /** Retrieves secrets from a single Vault path as a one-record feeder. */
  def apply(
      vaultUrl: String,
      secretPath: String,
      roleId: String,
      secretId: String,
      keys: List[String],
      timeoutInSeconds: Long = 5,
  ): IndexedSeq[Record[String]] = {
    require(keys != null, "Keys list must not be null")

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats

    val body   = approleLoginBody(roleId, secretId)
    val client = THttpClient(timeoutInSeconds = timeoutInSeconds)

    val vaultTokenResponse: String = client
      .post(s"""$vaultUrl/v1/auth/approle/login""", body)
      .body()

    val vaultToken = extractClientToken(parse(vaultTokenResponse))

    val getHeaders: Seq[String]   = Seq("X-Vault-Token", s"""$vaultToken""")
    val vaultDataResponse: String = client
      .get(s"""$vaultUrl/v1/$secretPath""", getHeaders)
      .body()

    val vaultDataJson: JValue = parse(vaultDataResponse)
    val data: Record[String]  = (vaultDataJson \ "data").extract[Map[String, String]]

    IndexedSeq(filterRecord(data, keys))
  }

  /** Retrieves secrets from multiple Vault paths and merges them into a single record.
    *
    * Uses [[DuplicateKeyStrategy.FailOnDuplicate]] by default.
    *
    * @param timeoutInSeconds
    *   connect and per-request timeout for Vault HTTP calls (default 5s)
    */
  def fromPaths(
      vaultUrl: String,
      roleId: String,
      secretId: String,
      paths: List[(String, List[String])],
      timeoutInSeconds: Long = 5,
  ): IndexedSeq[Record[String]] =
    fromPaths(vaultUrl, roleId, secretId, paths, DuplicateKeyStrategy.FailOnDuplicate, timeoutInSeconds)

  /** Retrieves secrets from multiple Vault paths with explicit duplicate-key strategy and default timeout.
    *
    * @param onDuplicate
    *   strategy when the same key appears in more than one path
    */
  def fromPaths(
      vaultUrl: String,
      roleId: String,
      secretId: String,
      paths: List[(String, List[String])],
      onDuplicate: DuplicateKeyStrategy,
  ): IndexedSeq[Record[String]] =
    fromPaths(vaultUrl, roleId, secretId, paths, onDuplicate, 5L)

  /** Retrieves secrets from multiple Vault paths with explicit duplicate-key strategy and timeout.
    *
    * @param onDuplicate
    *   strategy when the same key appears in more than one path
    * @param timeoutInSeconds
    *   connect and per-request timeout for Vault HTTP calls
    */
  def fromPaths(
      vaultUrl: String,
      roleId: String,
      secretId: String,
      paths: List[(String, List[String])],
      onDuplicate: DuplicateKeyStrategy,
      timeoutInSeconds: Long,
  ): IndexedSeq[Record[String]] = {
    require(paths != null, "Paths list must not be null")
    val allPairs = paths.flatMap { case (secretPath, keys) =>
      apply(vaultUrl, secretPath, roleId, secretId, keys, timeoutInSeconds).flatMap(_.toSeq)
    }
    IndexedSeq(mergeWithStrategy(allPairs, onDuplicate))
  }

  private def mergeWithStrategy(
      pairs: List[(String, String)],
      strategy: DuplicateKeyStrategy,
  ): Record[String] = {
    lazy val duplicates = pairs.groupBy(_._1).collect { case (k, vs) if vs.size > 1 => k }
    strategy match {
      case DuplicateKeyStrategy.FailOnDuplicate =>
        require(
          duplicates.isEmpty,
          s"Duplicate keys found across Vault paths: ${duplicates.mkString(", ")}. " +
            "Use distinct key names, fetch paths separately, or set onDuplicate = LastWins / FirstWins.",
        )
        pairs.toMap
      case DuplicateKeyStrategy.LastWins        =>
        if (duplicates.nonEmpty)
          logger.warn(s"Duplicate keys across Vault paths (last-writer-wins): ${duplicates.mkString(", ")}")
        pairs.toMap
      case DuplicateKeyStrategy.FirstWins       =>
        if (duplicates.nonEmpty)
          logger.warn(s"Duplicate keys across Vault paths (first-writer-wins): ${duplicates.mkString(", ")}")
        pairs.distinctBy(_._1).toMap
    }
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
      timeoutInSeconds: Long = 5,
  ): IndexedSeq[Record[String]] = {
    require(keys != null, "Keys list must not be null")

    implicit val formats: DefaultFormats = org.json4s.DefaultFormats

    val getHeaders: Seq[String]   = Seq("X-Vault-Token", vaultToken)
    val vaultDataResponse: String = THttpClient(timeoutInSeconds = timeoutInSeconds)
      .get(s"""$vaultUrl/v1/$secretPath""", getHeaders)
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
