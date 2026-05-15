package org.galaxio.gatling.feeders

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import org.galaxio.gatling.tags.DockerTest
import org.galaxio.gatling.utils.THttpClient
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

class VaultIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer {

  private val rootToken = "test-root-token"

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

  private def vaultExec(method: String, path: String, body: String = ""): String = {
    val client  = THttpClient()
    val headers = Seq("X-Vault-Token", rootToken)
    method match {
      case "GET"  => client.GET(s"$vaultUrl/v1/$path", headers).body()
      case "POST" => client.POSTJson(s"$vaultUrl/v1/$path", body, headers).body()
      case "PUT"  => client.POSTJson(s"$vaultUrl/v1/$path", body, headers).body()
    }
  }

  private def writeSecret(path: String, data: Map[String, String]): Unit = {
    val json = data.map { case (k, v) => s""""$k":"$v"""" }.mkString("{", ",", "}")
    vaultExec("POST", path, json)
  }

  private lazy val (appRoleId, appSecretId) = {
    vaultExec("POST", "sys/auth/approle", """{"type":"approle"}""")
    vaultExec("POST", "auth/approle/role/test-role", """{"policies":"default","token_ttl":"1h"}""")
    vaultExec("POST", "sys/policy/default", """{"policy":"path \"secret/*\" { capabilities = [\"read\",\"list\"] }"}""")

    val roleResp                         = vaultExec("GET", "auth/approle/role/test-role/role-id")
    implicit val formats: DefaultFormats = DefaultFormats
    val roleId                           = (JsonMethods.parse(roleResp) \ "data" \ "role_id").extract[String]

    val secretResp = vaultExec("POST", "auth/approle/role/test-role/secret-id", "")
    val secretId   = (JsonMethods.parse(secretResp) \ "data" \ "secret_id").extract[String]

    (roleId, secretId)
  }

  "VaultFeeder.withToken" should {
    "read secrets with token auth" taggedAs DockerTest in {
      writeSecret("secret/test/creds", Map("username" -> "admin", "password" -> "s3cret", "host" -> "db.local"))

      val result = VaultFeeder.withToken(vaultUrl, "secret/test/creds", rootToken, List("username", "password"))

      result should have size 1
      result.head shouldBe Map("username" -> "admin", "password" -> "s3cret")
    }

    "filter keys from response" taggedAs DockerTest in {
      writeSecret("secret/test/multi", Map("a" -> "1", "b" -> "2", "c" -> "3", "d" -> "4"))

      val result = VaultFeeder.withToken(vaultUrl, "secret/test/multi", rootToken, List("b", "d"))

      result.head shouldBe Map("b" -> "2", "d" -> "4")
    }

    "return empty map when no keys match" taggedAs DockerTest in {
      writeSecret("secret/test/nomatch", Map("x" -> "1"))

      val result = VaultFeeder.withToken(vaultUrl, "secret/test/nomatch", rootToken, List("nonexistent"))

      result should have size 1
      result.head shouldBe empty
    }
  }

  "VaultFeeder.apply (AppRole)" should {
    "authenticate and read secrets" taggedAs DockerTest in {
      writeSecret("secret/test/approle-data", Map("key1" -> "val1", "key2" -> "val2"))

      val result = VaultFeeder(vaultUrl, "secret/test/approle-data", appRoleId, appSecretId, List("key1", "key2"))

      result should have size 1
      result.head shouldBe Map("key1" -> "val1", "key2" -> "val2")
    }

    "filter keys with AppRole auth" taggedAs DockerTest in {
      writeSecret("secret/test/approle-filter", Map("keep" -> "yes", "drop" -> "no"))

      val result = VaultFeeder(vaultUrl, "secret/test/approle-filter", appRoleId, appSecretId, List("keep"))

      result.head shouldBe Map("keep" -> "yes")
    }
  }

  "VaultFeeder.fromPaths" should {
    "merge secrets from multiple paths" taggedAs DockerTest in {
      writeSecret("secret/test/path-a", Map("db_host" -> "localhost", "db_port" -> "5432"))
      writeSecret("secret/test/path-b", Map("api_key" -> "abc123", "api_url" -> "http://api"))

      val paths = List(
        ("secret/test/path-a", List("db_host", "db_port")),
        ("secret/test/path-b", List("api_key")),
      )

      val result = VaultFeeder.fromPaths(vaultUrl, appRoleId, appSecretId, paths)

      result should have size 1
      result.head shouldBe Map("db_host" -> "localhost", "db_port" -> "5432", "api_key" -> "abc123")
    }
  }
}
