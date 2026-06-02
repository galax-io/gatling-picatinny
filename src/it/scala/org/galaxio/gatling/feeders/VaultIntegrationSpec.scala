package org.galaxio.gatling.feeders

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.galaxio.gatling.utils.THttpClient
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

class VaultIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer {

  private val rootToken = "test-root-token"
  private val kvMount   = "picatinny"

  override val container: GenericContainer = GenericContainer(
    dockerImage = "hashicorp/vault:1.17",
    exposedPorts = Seq(8200),
    waitStrategy = Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200),
    env = Map(
      "VAULT_DEV_ROOT_TOKEN_ID"  -> rootToken,
      "VAULT_DEV_LISTEN_ADDRESS" -> "0.0.0.0:8200",
    ),
  )

  private def vaultUrl: String = s"http://localhost:${container.mappedPort(8200)}"

  private lazy val kvV1MountReady: Unit = {
    vaultExec("POST", s"sys/mounts/$kvMount", """{"type":"kv","options":{"version":"1"}}""")
    ()
  }

  private def vaultExec(method: String, path: String, body: String = ""): String = {
    val client  = THttpClient()
    val headers = Seq("X-Vault-Token", rootToken)
    method match {
      case "GET"  => client.get(s"$vaultUrl/v1/$path", headers).body
      case "POST" => client.post(s"$vaultUrl/v1/$path", body, headers).body
      case "PUT"  => client.put(s"$vaultUrl/v1/$path", body, headers).body
    }
  }

  private def writeSecret(path: String, data: Map[String, String]): Unit = {
    kvV1MountReady
    val json = data.map { case (k, v) => s""""$k":"$v"""" }.mkString("{", ",", "}")
    vaultExec("POST", path, json)
  }

  private lazy val (appRoleId, appSecretId) = {
    kvV1MountReady
    vaultExec("POST", "sys/auth/approle", """{"type":"approle"}""")
    vaultExec("POST", "auth/approle/role/test-role", """{"policies":"default","token_ttl":"1h"}""")
    vaultExec(
      "POST",
      "sys/policy/default",
      s"""{"policy":"path \\"$kvMount/*\\" { capabilities = [\\"read\\",\\"list\\"] }"}""",
    )

    val roleResp                         = vaultExec("GET", "auth/approle/role/test-role/role-id")
    implicit val formats: DefaultFormats = DefaultFormats
    val roleId                           = (JsonMethods.parse(roleResp) \ "data" \ "role_id").extract[String]

    val secretResp = vaultExec("POST", "auth/approle/role/test-role/secret-id", "")
    val secretId   = (JsonMethods.parse(secretResp) \ "data" \ "secret_id").extract[String]

    (roleId, secretId)
  }

  "VaultFeeder.withToken" should {
    "read secrets with token auth" in {
      writeSecret(s"$kvMount/test/creds", Map("username" -> "admin", "password" -> "s3cret", "host" -> "db.local"))

      val result = VaultFeeder.withToken(vaultUrl, s"$kvMount/test/creds", rootToken, List("username", "password"))

      result should have size 1
      result.head shouldBe Map("username" -> "admin", "password" -> "s3cret")
    }

    "filter keys from response" in {
      writeSecret(s"$kvMount/test/multi", Map("a" -> "1", "b" -> "2", "c" -> "3", "d" -> "4"))

      val result = VaultFeeder.withToken(vaultUrl, s"$kvMount/test/multi", rootToken, List("b", "d"))

      result.head shouldBe Map("b" -> "2", "d" -> "4")
    }

    "return empty map when no keys match" in {
      writeSecret(s"$kvMount/test/nomatch", Map("x" -> "1"))

      val result = VaultFeeder.withToken(vaultUrl, s"$kvMount/test/nomatch", rootToken, List("nonexistent"))

      result should have size 1
      result.head shouldBe empty
    }
  }

  "VaultFeeder.apply (AppRole)" should {
    "authenticate and read secrets" in {
      writeSecret(s"$kvMount/test/approle-data", Map("key1" -> "val1", "key2" -> "val2"))

      val result = VaultFeeder(vaultUrl, s"$kvMount/test/approle-data", appRoleId, appSecretId, List("key1", "key2"))

      result should have size 1
      result.head shouldBe Map("key1" -> "val1", "key2" -> "val2")
    }

    "filter keys with AppRole auth" in {
      writeSecret(s"$kvMount/test/approle-filter", Map("keep" -> "yes", "drop" -> "no"))

      val result = VaultFeeder(vaultUrl, s"$kvMount/test/approle-filter", appRoleId, appSecretId, List("keep"))

      result.head shouldBe Map("keep" -> "yes")
    }
  }

  "VaultFeeder.fromPaths" should {
    "merge secrets from multiple paths" in {
      writeSecret(s"$kvMount/test/path-a", Map("db_host" -> "localhost", "db_port" -> "5432"))
      writeSecret(s"$kvMount/test/path-b", Map("api_key" -> "abc123", "api_url" -> "http://api"))

      val paths = List(
        (s"$kvMount/test/path-a", List("db_host", "db_port")),
        (s"$kvMount/test/path-b", List("api_key")),
      )

      val result = VaultFeeder.fromPaths(vaultUrl, appRoleId, appSecretId, paths)

      result should have size 1
      result.head shouldBe Map("db_host" -> "localhost", "db_port" -> "5432", "api_key" -> "abc123")
    }

    "fail on duplicate keys by default" in {
      writeSecret(s"$kvMount/test/dup-a", Map("shared" -> "from-a", "only_a" -> "a"))
      writeSecret(s"$kvMount/test/dup-b", Map("shared" -> "from-b", "only_b" -> "b"))

      val paths = List(
        (s"$kvMount/test/dup-a", List("shared", "only_a")),
        (s"$kvMount/test/dup-b", List("shared", "only_b")),
      )

      val ex = the[IllegalArgumentException] thrownBy {
        VaultFeeder.fromPaths(vaultUrl, appRoleId, appSecretId, paths)
      }
      ex.getMessage should include("shared")
    }

    "keep last value with LastWins strategy" in {
      writeSecret(s"$kvMount/test/lw-a", Map("key" -> "first", "a" -> "1"))
      writeSecret(s"$kvMount/test/lw-b", Map("key" -> "second", "b" -> "2"))

      val paths = List(
        (s"$kvMount/test/lw-a", List("key", "a")),
        (s"$kvMount/test/lw-b", List("key", "b")),
      )

      val result = VaultFeeder.fromPaths(vaultUrl, appRoleId, appSecretId, paths, DuplicateKeyStrategy.LastWins)

      result.head("key") shouldBe "second"
      result.head("a") shouldBe "1"
      result.head("b") shouldBe "2"
    }

    "keep first value with FirstWins strategy" in {
      writeSecret(s"$kvMount/test/fw-a", Map("key" -> "first", "a" -> "1"))
      writeSecret(s"$kvMount/test/fw-b", Map("key" -> "second", "b" -> "2"))

      val paths = List(
        (s"$kvMount/test/fw-a", List("key", "a")),
        (s"$kvMount/test/fw-b", List("key", "b")),
      )

      val result = VaultFeeder.fromPaths(vaultUrl, appRoleId, appSecretId, paths, DuplicateKeyStrategy.FirstWins)

      result.head("key") shouldBe "first"
      result.head("a") shouldBe "1"
      result.head("b") shouldBe "2"
    }
  }
}
