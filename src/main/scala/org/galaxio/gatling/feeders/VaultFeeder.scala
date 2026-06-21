package org.galaxio.gatling.feeders

import com.typesafe.scalalogging.LazyLogging
import io.gatling.core.feeder.Record
import org.galaxio.gatling.utils.THttpClient
import org.json4s.native.JsonMethods.{compact, parse, render}
import org.json4s.{DefaultFormats, Extraction, JValue}

import scala.util.{Try, Using}

/** Strategy for handling duplicate keys when merging secrets from multiple Vault paths. */
sealed trait DuplicateKeyStrategy

object DuplicateKeyStrategy {

  /** Throw [[java.lang.IllegalArgumentException]] listing the colliding keys. */
  case object FailOnDuplicate extends DuplicateKeyStrategy

  /** Keep the value from the last path in the list (last-writer-wins). Logs a warning. */
  case object LastWins extends DuplicateKeyStrategy

  /** Keep the value from the first path in the list. Logs a warning. */
  case object FirstWins extends DuplicateKeyStrategy
}

object VaultFeeder extends LazyLogging {

  private implicit val formats: DefaultFormats = org.json4s.DefaultFormats

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

    Using.resource(THttpClient(timeoutInSeconds = timeoutInSeconds)) { client =>
      val vaultToken = login(client, vaultUrl, roleId, secretId)
      val data       = readSecret(client, vaultUrl, secretPath, vaultToken)
      IndexedSeq(filterRecord(data, keys))
    }
  }

  /** Retrieves secrets from multiple Vault paths and merges them into a single record.
    *
    * Uses [[DuplicateKeyStrategy.FailOnDuplicate]] by default. Authenticates once and reuses the token across all path reads.
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
    * Authenticates once and reuses the token for all path reads.
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

    Using.resource(THttpClient(timeoutInSeconds = timeoutInSeconds)) { client =>
      val vaultToken = login(client, vaultUrl, roleId, secretId)
      val allPairs   = paths.flatMap { case (secretPath, keys) =>
        val data = readSecret(client, vaultUrl, secretPath, vaultToken)
        filterRecord(data, keys).toSeq
      }
      IndexedSeq(mergeWithStrategy(allPairs, onDuplicate))
    }
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

    Using.resource(THttpClient(timeoutInSeconds = timeoutInSeconds)) { client =>
      val data = readSecret(client, vaultUrl, secretPath, vaultToken)
      IndexedSeq(filterRecord(data, keys))
    }
  }

  private def login(client: THttpClient, vaultUrl: String, roleId: String, secretId: String): String = {
    val body      = approleLoginBody(roleId, secretId)
    val loginUrl  = s"$vaultUrl/v1/auth/approle/login"
    val response  = client.postOrThrow(loginUrl, body)
    val loginJson = Try(parse(response.body)).fold(
      e => throw new RuntimeException(s"Failed to parse Vault login response as JSON from $loginUrl", e),
      identity,
    )
    Try(extractClientToken(loginJson)).fold(
      e => throw new RuntimeException("Failed to extract client_token from Vault login response", e),
      identity,
    )
  }

  private def readSecret(client: THttpClient, vaultUrl: String, secretPath: String, vaultToken: String): Record[String] = {
    val secretUrl = s"$vaultUrl/v1/$secretPath"
    val response  = client.getOrThrow(secretUrl, Seq("X-Vault-Token", vaultToken))
    val json      = Try(parse(response.body)).fold(
      e => throw new RuntimeException(s"Failed to parse Vault secret response as JSON from '$secretPath'", e),
      identity,
    )
    Try((json \ "data").extract[Map[String, String]]).fold(
      e => throw new RuntimeException(s"Failed to extract secret data from Vault response at '$secretPath'", e),
      identity,
    )
  }

  private def filterRecord(data: Record[String], keys: List[String]): Record[String] = {
    val selectedKeys = keys.toSet
    val result       = data.view.filterKeys(selectedKeys.contains).toMap
    if (result.isEmpty && keys.nonEmpty)
      logger.warn(
        s"None of the requested keys [${keys.mkString(", ")}] were found in the Vault secret. " +
          s"Available keys: [${data.keys.mkString(", ")}]",
      )
    result
  }

  private[feeders] def approleLoginBody(roleId: String, secretId: String): String =
    compact(render(Extraction.decompose(Map("role_id" -> roleId, "secret_id" -> secretId))))

  private[feeders] def extractClientToken(vaultTokenJson: JValue): String =
    (vaultTokenJson \ "auth" \ "client_token").extract[String]
}
