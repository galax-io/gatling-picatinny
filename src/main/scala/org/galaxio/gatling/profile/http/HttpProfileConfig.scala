package org.galaxio.gatling.profile.http

import org.galaxio.gatling.profile.ProfileConfig

case class HttpProfileConfig(name: String, profile: List[HttpRequestConfig]) extends ProfileConfig
