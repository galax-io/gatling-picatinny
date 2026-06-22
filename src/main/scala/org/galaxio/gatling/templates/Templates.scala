package org.galaxio.gatling.templates

import java.nio.file.{Files, Paths}

import io.gatling.core.Predef._
import io.gatling.core.body.Body
import io.gatling.core.session.Expression
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.jdk.CollectionConverters._

/** Loads template files from `resources/templates` and provides methods to send them as HTTP request bodies.
  *
  * Mix this trait into a Gatling Simulation or Scenario class. Template files support
  * [[https://gatling.io/docs/gatling/reference/current/session/expression_el/ Gatling EL expressions]].
  *
  * {{{
  * class MyScenario extends Templates {
  *   val scn = scenario("example")
  *     .exec(postTemplate("my_template", "/api/endpoint"))
  * }
  * }}}
  *
  * Templates are lazily loaded on first access. If the `templates` resource directory is absent from the classpath, the first
  * access fails fast with an [[IllegalStateException]] naming the missing directory (rather than silently yielding an empty
  * map). A present-but-empty directory yields an empty registry.
  */
trait Templates {

  /** Map of template name (filename without extension) to Gatling EL file body. Lazily initialized from the `templates`
    * resource directory. Fails fast with an [[IllegalStateException]] if that directory is absent from the classpath; a
    * present-but-empty directory yields an empty map.
    */
  protected lazy val templates: Map[String, Body with Expression[String]] =
    Option(Thread.currentThread.getContextClassLoader.getResource("templates")) match {
      case None           =>
        throw new IllegalStateException(
          "Templates directory 'templates' was not found on the classpath. Expected a 'templates' resource directory " +
            "(e.g. src/main/resources/templates or src/test/resources/templates). Check the directory name and that it " +
            "is present on the runtime classpath.",
        )
      case Some(resource) =>
        Files
          .list(Paths.get(resource.toURI))
          .iterator()
          .asScala
          .map(_.toFile)
          .filter(_.isFile)
          .map { f =>
            val name   = f.getName
            val dotIdx = name.lastIndexOf('.')
            val key    = if (dotIdx > 0) name.substring(0, dotIdx) else name
            (key, ElFileBody(f.getCanonicalPath))
          }
          .toMap
    }

  private def resolveTemplate(templateName: String): Body with Expression[String] =
    templates.getOrElse(
      templateName,
      throw new NoSuchElementException(
        s"Template '$templateName' not found. Available: ${templates.keys.mkString(", ")}",
      ),
    )

  /** Sends a POST request with the named template as body.
    *
    * @param templateName
    *   filename without extension from `resources/templates`
    * @param targetUrl
    *   target URL path
    * @throws NoSuchElementException
    *   if template name is not found
    */
  def postTemplate(templateName: String, targetUrl: String): HttpRequestBuilder =
    http(templateName)
      .post(targetUrl)
      .body(resolveTemplate(templateName))
}
