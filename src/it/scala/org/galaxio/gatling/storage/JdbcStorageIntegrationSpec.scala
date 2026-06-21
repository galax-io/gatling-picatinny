package org.galaxio.gatling.storage

import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import io.gatling.core.feeder.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.wait.strategy.Wait

/** External-integration test (test-model layer 3) for [[JdbcStorageBackend]] against a REAL PostgreSQL container — NOT the
  * `RecordingJdbcDriver` proxy fake (asserting that fake would be mock-vs-mock). It drives the real `save`/`load`/`clear` SQL
  * path through a real JDBC driver and asserts the exact values that survive the round-trip, including insertion order and an
  * empty-table boundary.
  */
class JdbcStorageIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer {

  // Force-register the Postgres driver. DriverManager runs its ServiceLoader scan ONCE, on first use; if the
  // earlier Test phase initialized it under a classpath without the `it`-scoped driver, it would never be found
  // here ("No suitable driver found"). Loading the class triggers its static registerDriver regardless.
  Class.forName("org.postgresql.Driver")

  private val dbName = "picatinny"
  private val dbUser = "picatinny"
  private val dbPass = "picatinny-secret"

  override val container: GenericContainer = GenericContainer(
    dockerImage = "postgres:17-alpine",
    exposedPorts = Seq(5432),
    env = Map(
      "POSTGRES_DB"       -> dbName,
      "POSTGRES_USER"     -> dbUser,
      "POSTGRES_PASSWORD" -> dbPass,
    ),
    // Postgres logs the readiness line twice (init bootstrap, then real start).
    waitStrategy = Wait.forLogMessage(".*database system is ready to accept connections.*", 2),
  )

  private def jdbcUrl: String = s"jdbc:postgresql://localhost:${container.mappedPort(5432)}/$dbName"

  private def backend(table: String): JdbcStorageBackend =
    JdbcStorageBackend(jdbcUrl, tableName = table, username = dbUser, password = dbPass)

  private def rec(pairs: (String, Any)*): Record[Any] = pairs.toMap

  "JdbcStorageBackend against a real PostgreSQL container" should {

    "create the table and round-trip saved records in insertion order" in {
      val be      = backend("rt_order")
      val records = Seq(rec("k" -> "first"), rec("k" -> "second"), rec("k" -> "third"))
      be.save(records)
      be.load().map(_("k")) shouldBe Seq("first", "second", "third")
    }

    "round-trip a multi-field record exactly" in {
      val be = backend("rt_fields")
      be.save(Seq(rec("name" -> "alice", "city" -> "NY")))
      be.load() shouldBe Seq(Map("name" -> "alice", "city" -> "NY"))
    }

    "clear() removes all rows" in {
      val be = backend("rt_clear")
      be.save(Seq(rec("k" -> "v")))
      be.load() should not be empty
      be.clear()
      be.load() shouldBe empty
    }

    "load() returns empty for a freshly created table (boundary)" in {
      backend("rt_empty").load() shouldBe empty
    }
  }
}
