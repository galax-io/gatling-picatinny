package org.galaxio.gatling.utils.jwt

/** JWT payload JSON wrapper. Supports Gatling EL expressions. */
final case class Payload(json: String = "")
