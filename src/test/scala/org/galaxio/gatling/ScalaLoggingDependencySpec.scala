package org.galaxio.gatling

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ScalaLoggingDependencySpec extends AnyWordSpec with Matchers with LazyLogging {

  "scala-logging" should {
    "be available on the test classpath" in {
      logger.info("scala-logging is available for LazyLogging-based code paths")
      succeed
    }
  }
}
