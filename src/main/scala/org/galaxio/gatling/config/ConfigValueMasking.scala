package org.galaxio.gatling.config

import com.typesafe.config.Config

import java.net.URI
import java.util.regex.{Matcher, Pattern}
import scala.jdk.CollectionConverters._
import scala.util.Try

/** Central secret-redaction helper applied at every log/print/exception sink.
  *
  * Sensitivity is decided on a key's LAST path segment, split into words across camelCase/snake/kebab boundaries, by three
  * rules (NOT raw substring):
  *   - '''compound''' — a contiguous word run equals a multi-word secret term (`apiKey`→`apikey`, `client_secret`,
  *     `private_key`); also how user-supplied terms match;
  *   - '''strong''' — a single standalone secret word (`password`, `secret`, `token`, `bearer`, …) appears, UNLESS it is
  *     immediately followed by a benign structural tail (`tokenBucketSize`, `passwordLength` → not masked);
  *   - '''suffix floor''' — a separator-less key whose joined segment ENDS WITH a secret noun (`dbpassword`, `apisecret`,
  *     `accesstoken`) is masked, so coverage is never narrower than the old substring matcher; the head-noun anchor keeps
  *     prefix-only compounds (`tokenBucketSize`) visible.
  *
  * This masks `apiKey`, `bearerToken`, `clientSecret`, `db.password`, `passwordHash`, `tokenValue`, `secret_id` while leaving
  * non-secret identifiers (`roleId`, `roleIdPrefix`) and benign compounds (`tokenBucketSize`, `apiKeyboard`, `secretariat`)
  * visible. The term set is the built-in floor MERGED with any user-supplied `picatinny.redaction.additionalSensitiveKeys`
  * (merge-not-replace). Use [[ConfigValueMasking.fromConfig]] for the config-aware instance; the object-level methods use the
  * built-in floor only.
  */
private[gatling] final class ConfigValueMasking(
    strongTerms: Set[String],
    compoundTerms: Set[String],
    benignTails: Set[String],
    replacement: String,
) {

  def isSensitive(path: String): Boolean = {
    val segmentWords = ConfigValueMasking.words(path)
    compoundMatch(segmentWords) || strongMatch(segmentWords) || suffixFloor(segmentWords)
  }

  def displayValue(path: String, value: Any): String =
    if (isSensitive(path)) replacement else String.valueOf(value)

  /** The placeholder this instance substitutes for sensitive values (built-in `******` unless overridden in config). */
  def placeholder: String = replacement

  /** Render a (possibly nested) config block leaf-by-leaf, masking each leaf by its own key. `entrySet()` returns
    * already-flattened leaf paths (e.g. `a.b.secret`), so each leaf's last segment drives the masking decision — a benign block
    * name can no longer hide a secret child.
    */
  def displayConfig(cfg: Config): String =
    cfg
      .entrySet()
      .asScala
      .toSeq
      .sortBy(_.getKey)
      .map(entry => s"${entry.getKey} = ${displayValue(entry.getKey, entry.getValue.unwrapped)}")
      .mkString("\n")

  private def compoundMatch(segmentWords: List[String]): Boolean =
    ConfigValueMasking.suffixRuns(segmentWords).exists(compoundTerms.contains)

  private def strongMatch(segmentWords: List[String]): Boolean =
    segmentWords.zipWithIndex.exists { case (word, idx) =>
      strongTerms.contains(word) && segmentWords.lift(idx + 1).forall(next => !benignTails.contains(next))
    }

  /** Safety floor against masking regressions: a separator-less key (`dbpassword`, `apisecret`, `accesstoken`) is a single word
    * that the word-boundary rules miss, yet it ends with a secret noun. Mask when the joined segment ENDS WITH a strong or
    * compound term — this is the head-noun, so `tokenBucketSize`/`secretariat`/`apiKeyboard` (secret word is a prefix, head is
    * benign) stay visible. Masking may only loosen, never tighten, versus the prior substring behavior.
    */
  private def suffixFloor(segmentWords: List[String]): Boolean = {
    val joined = segmentWords.mkString
    joined.nonEmpty && (strongTerms.exists(joined.endsWith) || compoundTerms.exists(joined.endsWith))
  }
}

private[gatling] object ConfigValueMasking {

  /** Standalone secret words — match in any position on the segment (subject to the benign-tail guard). */
  private val StrongTerms: Set[String] =
    Set("password", "passwd", "pwd", "secret", "token", "credential", "credentials", "passphrase", "authorization", "bearer")

  /** Multi-word secret terms — match only as an exact contiguous (suffix-anchored) word run, e.g. `apiKey`→`apikey`. */
  private val CompoundTerms: Set[String] =
    Set("apikey", "privatekey", "clientsecret", "accesskey", "secretkey")

  /** Structural words that, immediately AFTER a strong term, mark a non-secret metadata key (count/duration/size), e.g.
    * `tokenBucketSize`, `passwordLength`, `secretRotation`. Deliberately excludes ambiguous tails like `id`/`name` so genuine
    * credentials (`secret_id`) stay masked.
    */
  private val BenignTails: Set[String] =
    Set("bucket", "size", "count", "limit", "length", "timeout", "ttl", "interval", "expiry", "rotation", "age")

  val Replacement: String = "******"

  private val ExtraKeysPath      = "picatinny.redaction.additionalSensitiveKeys"
  private val ReplacementCfgPath = "picatinny.redaction.replacement"

  /** Precompiled once — `String.replaceAll` would recompile this on every call. */
  private val CamelBoundary: Pattern = Pattern.compile("([a-z0-9])([A-Z])")

  /** The built-in-floor instance (no user-configured terms). Used by the object-level helpers and as the explicit argument when
    * a caller has no `Config` to derive from (e.g. tests).
    */
  private[gatling] val builtin = new ConfigValueMasking(StrongTerms, CompoundTerms, BenignTails, Replacement)

  def isSensitive(path: String): Boolean             = builtin.isSensitive(path)
  def displayValue(path: String, value: Any): String = builtin.displayValue(path, value)
  def displayConfig(cfg: Config): String             = builtin.displayConfig(cfg)

  /** Build a config-aware masking instance: built-in terms MERGED with `picatinny.redaction.additionalSensitiveKeys` (never
    * replaced; user terms join the compound set). An absent block falls back to the built-in floor. The placeholder may be
    * overridden via `picatinny.redaction.replacement`.
    */
  def fromConfig(config: Config): ConfigValueMasking = {
    val extra       = stringListAt(config, ExtraKeysPath).iterator.flatMap(normalizedTerm).toSet
    val replacement = stringAt(config, ReplacementCfgPath).getOrElse(Replacement)
    new ConfigValueMasking(StrongTerms, CompoundTerms ++ extra, BenignTails, replacement)
  }

  /** Strip URL userinfo (`user:password@`) before logging. Fail-safe: never throws, never returns the raw credential.
    *   - parses with [[java.net.URI]]; if userinfo present, replaces the FIRST `userinfo@` run with the placeholder
    *   - parsed but no userinfo → original returned unchanged (incl. opaque URIs like `mailto:`)
    *   - unparseable → regex strip, then a conservative fully-redacted placeholder if even that fails to match
    *   - `null` input → empty string (callers log the result; never a NPE)
    */
  def redactUserInfo(raw: String): String =
    Option(raw).fold("") { value =>
      Try(new URI(value)).fold(
        _ => fallbackRedact(value),
        uri =>
          userInfoOf(uri)
            .filter(_.nonEmpty)
            .fold(value)(userInfo =>
              value.replaceFirst(Pattern.quote(s"$userInfo@"), Matcher.quoteReplacement(s"$Replacement@")),
            ),
      )
    }

  private def userInfoOf(uri: URI): Option[String] =
    Option(uri.getRawUserInfo)
      .orElse(Option(uri.getRawAuthority).filter(_.contains('@')).map(_.takeWhile(_ != '@')))

  private def fallbackRedact(value: String): String =
    value.replaceFirst("://[^/@\\s]*@", s"://$Replacement@") match {
      case stripped if stripped != value => stripped
      case _ if value.contains('@')      => Replacement
      case unchanged                     => unchanged
    }

  private def stringListAt(config: Config, path: String): collection.Seq[String] =
    if (config.hasPath(path)) config.getStringList(path).asScala else Nil

  private def stringAt(config: Config, path: String): Option[String] =
    Option.when(config.hasPath(path))(config.getString(path))

  private def words(path: String): List[String] =
    splitWords(lastSegment(path))

  private def lastSegment(path: String): String =
    Option(path).getOrElse("") match {
      case ""      => ""
      case present => present.split(Array('.', '/')).filter(_.nonEmpty).lastOption.getOrElse(present)
    }

  /** Split a path segment into lowercase words across camelCase, snake_case and kebab-case boundaries. */
  private def splitWords(segment: String): List[String] =
    Option(segment)
      .filter(_.nonEmpty)
      .toList
      .flatMap(seg => CamelBoundary.matcher(seg).replaceAll("$1 $2").split(Array('_', '-', ' ')))
      .filter(_.nonEmpty)
      .map(_.toLowerCase)

  /** Contiguous word runs that END at the last word: for `[a,b,c]` → `"abc"`, `"bc"`, `"c"`. The secret noun is the head (last
    * element) of an English compound (`bearer token`, `client secret`, `api key`), so suffix-anchored runs match those while
    * sparing prefix uses like `token bucket size`.
    */
  private def suffixRuns(segmentWords: List[String]): List[String] =
    segmentWords.tails.collect { case run if run.nonEmpty => run.mkString }.toList

  private def normalizedTerm(term: String): Option[String] = Some(splitWords(term).mkString).filter(_.nonEmpty)
}
