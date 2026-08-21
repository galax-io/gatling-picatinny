package org.galaxio.gatling.templates

import io.gatling.core.Predef._
import io.gatling.core.body.Body
import io.gatling.core.session.Expression
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import java.nio.file.{Files, Paths}
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
  * access fails fast with an `IllegalStateException` naming the missing directory (rather than silently yielding an empty map).
  * A present-but-empty directory yields an empty registry.
  */
trait Templates {

  private val TemplatesDir = "templates/"

  /** Map of template name (filename without extension) to Gatling EL file body. Lazily initialized from the `templates`
    * resource directory. Fails fast with an `IllegalStateException` if that directory is absent from the classpath; a
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
        // Resolve names WITHOUT touching the filesystem path: the `templates` directory is a
        // classpath resource, and for a packaged consumer it lives inside a jar, where
        // `Paths.get(resource.toURI)` throws FileSystemNotFoundException. Handle both layouts.
        val names: List[String] = resource.openConnection() match {
          case jarConnection: java.net.JarURLConnection =>
            jarConnection.setUseCaches(true)
            jarConnection.getJarFile
              .entries()
              .asScala
              .map(_.getName)
              .collect {
                case entry if entry.startsWith(TemplatesDir) && !entry.endsWith("/") =>
                  entry.substring(TemplatesDir.length)
              }
              .filterNot(_.contains('/'))
              .toList
          case _                                        =>
            Files
              .list(Paths.get(resource.toURI))
              .iterator()
              .asScala
              .filter(Files.isRegularFile(_))
              .map(_.getFileName.toString)
              .toList
        }
        names.map { name =>
          val dotIdx = name.lastIndexOf('.')
          val key    = if (dotIdx > 0) name.substring(0, dotIdx) else name
          // Classpath-RELATIVE, never an absolute path: Gatling rejects an absolute path that
          // resolves inside its configured resources directory ("It should not be an absolute path
          // pointing to a directory that belongs to your classpath") — which is what a consumer's
          // build looks like whenever resource directories sit on the classpath instead of being
          // copied into the class directory, i.e. sbt 2's default.
          (key, ElFileBody(TemplatesDir + name))
        }.toMap
    }

  private def resolveTemplate(templateName: String): Body with Expression[String] =
    templates.getOrElse(
      templateName,
      throw new NoSuchElementException(
        s"Template '$templateName' not found. Available: ${templates.keys.mkString(", ")}",
      ),
    )

  /** Sends a POST request with the named template as body. Throws `NoSuchElementException` if `templateName` is not found.
    *
    * @param templateName
    *   filename without extension from `resources/templates`
    * @param targetUrl
    *   target URL path
    */
  def postTemplate(templateName: String, targetUrl: String): HttpRequestBuilder =
    http(templateName)
      .post(targetUrl)
      .body(resolveTemplate(templateName))
}
