package org.galaxio.gatling.storage

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JdbcStorageBackendSpec extends AnyWordSpec with Matchers {

  "JdbcStorageBackend" should {

    "close the result set when load fails" in {
      val driver = new JdbcTestSupport.RecordingJdbcDriver("jdbc:recording:", Seq("not-json"))

      JdbcTestSupport.withRegisteredDriver(driver) {
        val backend = JdbcStorageBackend("jdbc:recording:storage")

        intercept[Exception] {
          backend.load()
        }
      }

      driver.state.resultSetCloseCount.get() shouldBe 1
      driver.state.statementCloseCount.get() shouldBe 1
      driver.state.connectionCloseCount.get() shouldBe 1
    }

    "initialize the table only once per backend instance" in {
      val driver = new JdbcTestSupport.RecordingJdbcDriver("jdbc:recording:")

      JdbcTestSupport.withRegisteredDriver(driver) {
        val backend = JdbcStorageBackend("jdbc:recording:storage")

        backend.save(Seq(Map[String, Any]("user" -> "alice")))
        backend.load()
        backend.clear()
      }

      driver.state.ddlCount.get() shouldBe 1
      driver.state.executeBatchCount.get() shouldBe 1
      driver.state.queryCount.get() shouldBe 1
      driver.state.statementCloseCount.get() shouldBe 3
    }

    "initialize the table before clearing a fresh backend" in {
      val driver = new JdbcTestSupport.RecordingJdbcDriver("jdbc:recording:")

      JdbcTestSupport.withRegisteredDriver(driver) {
        val backend = JdbcStorageBackend("jdbc:recording:storage")

        noException should be thrownBy backend.clear()
      }

      driver.state.ddlCount.get() shouldBe 1
      driver.state.statementCloseCount.get() shouldBe 2
      driver.state.connectionCloseCount.get() shouldBe 1
    }

  }

}
