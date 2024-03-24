package io.cosmospf.gatling.profile.http

import io.cosmospf.gatling.profile.ProfileConfig

case class HttpProfileConfig(name: String, profile: List[HttpRequestConfig]) extends ProfileConfig
