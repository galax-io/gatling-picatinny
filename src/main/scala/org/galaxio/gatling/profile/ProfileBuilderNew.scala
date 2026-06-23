package org.galaxio.gatling.profile

import io.circe.yaml._
import io.circe.generic.auto._
import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import org.galaxio.gatling.utils.IntensityConverter.getIntensityFromString

import cats.syntax.either._

import java.io.FileNotFoundException
import java.nio.file.{Path, Paths}
import scala.io.Source
import scala.util.Using
import scala.util.matching.Regex

case class Params(method: String, path: String, headers: Option[List[String]], body: Option[String])

case class Request(request: String, intensity: String, groups: Option[List[String]], params: Params) {

  val requestIntensity: Double     = getIntensityFromString(intensity)
  val regexHeader: Regex           = """(.+?): (.+)""".r
  val requestBody: String          = params.body.getOrElse("")
  val requestHeaders: List[String] = params.headers.getOrElse(List.empty[String])

  /** Parses raw header lines into a name→value map, failing loudly on any line that is not `Name: Value`.
    *
    * `private[gatling]` (not `private[profile]`) so the Java/Kotlin facade in `javaapi.internal` delegates here instead of
    * duplicating the parsing logic.
    */
  private[gatling] lazy val parsedHeaders: Map[String, String] =
    requestHeaders.map {
      case regexHeader(a, b) => (a, b)
      case bad               =>
        throw ProfileBuilderNew.ProfileBuilderException(
          s"Malformed header: '$bad'. Expected format is 'Name: Value'",
          new IllegalArgumentException(bad),
        )
    }.toMap

  def toRequest: HttpRequestBuilder = {
    http(request)
      .httpRequest(params.method, params.path)
      .body(StringBody(requestBody))
      .headers(parsedHeaders)
  }

  def toExec: ChainBuilder            = exec(toRequest)
  def toTuple: (Double, ChainBuilder) = (requestIntensity, toExec)
}

case class OneProfile(name: String, period: String, protocol: String, profile: List[Request]) {

  def toRandomScenario: ScenarioBuilder = {
    val requests: List[(Double, ChainBuilder)]     = profile.map(request => request.toTuple)
    val intensitySum: Double                       = requests.map { case (intensity, _) => intensity }.sum
    val prepRequests: List[(Double, ChainBuilder)] =
      requests.map { case (intensity, chain) => (100 * intensity / intensitySum, chain) }

    scenario(name)
      .randomSwitch(prepRequests: _*)
  }
}

case class Metadata(name: String, description: String)

case class ProfileSpec(profiles: List[OneProfile])

case class Yaml(apiVersion: String, kind: String, metadata: Metadata, spec: ProfileSpec) {

  def selectProfile(profileName: String): OneProfile = {
    spec.profiles
      .find(_.name == profileName)
      .getOrElse(throw new NoSuchElementException(s"Selected wrong profile: $profileName"))
  }
}

object ProfileBuilderNew {

  final case class ProfileBuilderException(msg: String, cause: Throwable) extends Throwable(msg, cause, false, false)

  private def toProfileBuilderException(path: String): PartialFunction[Throwable, ProfileBuilderException] = {
    case e: SecurityException     => ProfileBuilderException(e.getMessage, e)
    case e: FileNotFoundException => ProfileBuilderException(s"File not found $path", e)
    case e: io.circe.Error        => ProfileBuilderException(s"Incorrect file content in $path", e)
    case e: Throwable             => ProfileBuilderException(s"Unknown error", e)
  }

  /** Verifies the caller path stays inside the working directory; guards against `../` traversal and absolute paths.
    *
    * An absolute caller path is rejected outright: `Paths.get(base, "/abs")` re-roots under `base` (the leading slash is
    * treated as a separator, not an absolute root), which would silently mask the caller's intent — so we reject it rather than
    * resolve it.
    */
  private def validateContainment(rawPath: String, fullPath: Path): Either[Throwable, Path] = {
    if (Paths.get(rawPath).isAbsolute)
      Left(new SecurityException(s"Path traversal detected: absolute path '$rawPath' is not allowed"))
    else {
      val base     = Paths.get(sys.props.getOrElse("user.dir", "")).toAbsolutePath.normalize()
      val resolved = fullPath.toAbsolutePath.normalize()
      Either.cond(
        resolved.startsWith(base),
        resolved,
        new SecurityException(s"Path traversal detected: resolved path '$resolved' escapes project base '$base'"),
      )
    }
  }

  def buildFromYaml(path: String): Yaml = {
    val attemptToParse = for {
      fullPath    <- sys.props
                       .get("user.dir")
                       .toRight(new NoSuchElementException("'user.dir' property not defined"))
                       .map(Paths.get(_, path))
      safePath    <- validateContainment(path, fullPath)
      yamlContent <- Using(Source.fromFile(safePath.toFile))(_.mkString).toEither
      parsed      <- parser.parse(yamlContent).flatMap(_.as[Yaml])
    } yield parsed

    attemptToParse.leftMap(toProfileBuilderException(path)).toTry.get
  }

  def buildFromYamlJava(path: String): Yaml = {
    try {
      val fullPath    = Paths.get(sys.props.toMap.apply("user.dir"), path)
      val safePath    = validateContainment(path, fullPath).toTry.get
      val yamlContent = Using.resource(Source.fromFile(safePath.toFile))(_.mkString)
      parser.parse(yamlContent).flatMap(_.as[Yaml]).toTry.get
    } catch {
      case e: SecurityException     => throw ProfileBuilderException(e.getMessage, e)
      case e: FileNotFoundException => throw ProfileBuilderException(s"File not found $path", e)
      case e: io.circe.Error        => throw ProfileBuilderException(s"Incorrect file content in $path", e)
      case e: Exception             => throw ProfileBuilderException(s"Unknown error", e)
    }
  }
}
