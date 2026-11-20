package org.galaxio.gatling.utils.jwt

import io.gatling.core.session.Expression
import io.gatling.core.session.el._

import scala.util.Try

/** JWT header JSON wrapper. Supports Gatling EL expressions. */
final case class Header(json: String = "") {

  /** `json` compiled once per instance, at the first token. A malformed EL is kept and thrown again on every token. */
  private[jwt] lazy val expression: Try[Expression[String]] = Try(json.el[String])
}
