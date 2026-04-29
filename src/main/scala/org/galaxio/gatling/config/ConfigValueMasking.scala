package org.galaxio.gatling.config

private[config] object ConfigValueMasking {
  private val SensitivePathTokens = Seq(
    "password",
    "passwd",
    "pwd",
    "secret",
    "token",
    "apikey",
    "api-key",
    "api_key",
    "credential",
  )

  def displayValue(path: String, value: Any): String =
    if (isSensitive(path)) "******" else String.valueOf(value)

  def isSensitive(path: String): Boolean = {
    val normalized = Option(path).getOrElse("").toLowerCase
    SensitivePathTokens.exists(normalized.contains)
  }
}
