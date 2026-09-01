package org.galaxio.gatling.templates

/** DSL for building JSON and XML request bodies with Gatling EL expression support.
  *
  * {{{
  * import org.galaxio.gatling.templates.Syntax._
  *
  * makeJson(
  *   "id" - 42,
  *   "name" ~ "userName",
  *   "tags" > ("a", "b", "c"),
  *   "nested" - ("key" - "value", "count" - 1),
  * )
  * }}}
  *
  * ==DSL Operators==
  *   - `"field" - value` — literal value
  *   - `"field" ~ "var"` — Gatling session variable reference `#{var}`
  *   - `"field" > (items)` — array
  *   - `"field" - (fields)` — nested object
  *   - `"field"` (implicit) — session variable with same name `#{field}`
  */
object Syntax {

  /** A named field in a JSON/XML structure. */
  case class Field(name: String, fieldVal: FieldVal)

  /** ADT representing possible field values in the template DSL. */
  sealed trait FieldVal

  /** A literal string value, rendered with quotes in JSON and escaped for special characters. */
  case class RawValString(value: String) extends FieldVal

  /** A literal non-string value (Int, Double, Boolean, etc.), rendered without quotes. */
  case class RawValGen[+T](value: T) extends FieldVal

  /** A Gatling EL string reference, rendered as `"#{name}"` in JSON. */
  case class InterpolateStrVal(interpolatorName: String) extends FieldVal

  /** A Gatling EL non-string reference, rendered as `#{name}` (without quotes) in JSON. */
  case class InterpolateGenVal[+T](interpolatorName: T) extends FieldVal

  /** A nested object containing a list of fields. */
  case class ObjectVal(f: List[Field]) extends FieldVal

  /** An array of field values. */
  case class ArrayVal(vs: List[FieldVal]) extends FieldVal

  /** Represents a JSON `null` value. Renders as `null` in JSON and `<tag/>` in XML. */
  case object NullVal extends FieldVal

  /** Convenience accessor for [[NullVal]]. Use as `"field" - nullVal`. */
  val nullVal: NullVal.type = NullVal

  /** Returns an empty JSON object string `{}`. */
  def emptyJson: String = "{}"

  /** Returns an empty JSON array string `[]`. */
  def emptyArr: String = "[]"

  /** Creates a nested [[ObjectVal]] from the given fields.
    *
    * {{{
    * val point = obj("x" - 1, "y" - 2)
    * makeJson("point" - point) // {"point": {"x": 1,"y": 2}}
    * }}}
    */
  def obj(fs: Field*): ObjectVal = ObjectVal(fs.toList)

  private val interpolateRegExpr = "#\\{([\\w.\\-]+)\\}".r

  /** Creates an [[ArrayVal]] from the given values, auto-detecting Gatling EL expressions in strings.
    *
    * Strings matching `#{varName}` are treated as [[InterpolateStrVal]]; other strings become [[RawValString]]. [[ObjectVal]]
    * and [[ArrayVal]] values pass through unchanged.
    */
  def arr[T](vs: T*): ArrayVal =
    ArrayVal(vs.map {
      case o: ObjectVal => o
      case a: ArrayVal  => a
      case f: Field     => ObjectVal(List(f))
      case s: String    =>
        // A string that is *entirely* an EL reference (`#{name}`, incl. dotted/hyphenated names) is an
        // EL value; any other string — including literal text mixed with EL — is kept whole and escaped.
        s match {
          case interpolateRegExpr(name) => InterpolateStrVal(name)
          case _                        => RawValString(s)
        }
      case other        => RawValGen(other)
    }.toList)

  /** Implicit conversion: a bare `"fieldName"` becomes a session variable reference `#{fieldName}`. */
  implicit def strToField(str: String): Field = Field(str, InterpolateStrVal(str))

  /** Enriches a String with DSL operators for building template fields.
    *
    * {{{
    * "name" - "literal"       // RawValString
    * "count" - 42             // RawValGen
    * "ref" ~ "sessionVar"     // InterpolateStrVal
    * "items" > (1, 2, 3)      // ArrayVal
    * "nested" - ("a" - 1)     // ObjectVal
    * }}}
    */
  implicit class FieldValOps(val fieldName: String) extends AnyVal {

    /** Creates a field with a literal string value. */
    def -(str: String): Field = Field(fieldName, RawValString(str))

    /** Creates a field with a typed value (array, object, null, or raw). */
    def -[T](v: T): Field = v match {
      case a: ArrayVal  => Field(fieldName, a)
      case o: ObjectVal => Field(fieldName, o)
      case NullVal      => Field(fieldName, NullVal)
      case other        => Field(fieldName, RawValGen(other))
    }

    /** Creates a field with a nested object from the given child fields. */
    def -(fs: Field*): Field = Field(fieldName, ObjectVal(fs.toList))

    /** Creates a field referencing a Gatling session string variable. Alias for `~`. */
    def asSessionVar(str: String): Field = Field(fieldName, InterpolateStrVal(str))

    /** Creates a field referencing a Gatling session string variable `#{str}`. */
    def ~(str: String): Field = Field(fieldName, InterpolateStrVal(str))

    /** Creates a field referencing a Gatling session non-string variable. Alias for `~`. */
    def asSessionVar[T](t: T): Field = Field(fieldName, InterpolateGenVal(t))

    /** Creates a field referencing a Gatling session non-string variable `#{t}`. */
    def ~[T](t: T): Field = Field(fieldName, InterpolateGenVal(t))

    /** Creates a field with an array value from a sequence. */
    def array[T](ts: Seq[T]): Field = Field(fieldName, arr(ts: _*))

    /** Creates a field with an array value. Shorthand: `"items" > (1, 2, 3)`. */
    def >[T](ts: T*): Field = array(ts)
  }

  private val HexDigits = "0123456789abcdef"

  /** Does this character need escaping in JSON?
    *
    * MUST agree, character for character, with the branch list in [[appendEscapedJson]] — this predicate is what lets the fast
    * path skip a whole string without inspecting it, so a character the match escapes but this misses would be emitted RAW. It
    * agrees today for a reason worth stating: `\n \r \t \b \f` all sit below `' '`, so the single `c < ' '` test covers them.
    * Add a dedicated escape for any PRINTABLE character (`/` is the usual candidate) and you must add it here too.
    */
  private def jsonNeedsEscape(c: Char): Boolean = c == '"' || c == '\\' || c < ' '

  /** Same contract as [[jsonNeedsEscape]], for the XML set — which is deliberately DIFFERENT: XML escapes five printable
    * characters and leaves control characters alone.
    */
  private def xmlNeedsEscape(c: Char): Boolean =
    c == '&' || c == '<' || c == '>' || c == '"' || c == '\''

  /** Index of the first character needing a JSON escape, or `s.length` when there is none. */
  private def jsonEscapeStart(s: String): Int = {
    val n = s.length
    var i = 0
    while (i < n && !jsonNeedsEscape(s.charAt(i))) i += 1
    i
  }

  /** Index of the first character needing an XML escape, or `s.length` when there is none. */
  private def xmlEscapeStart(s: String): Int = {
    val n = s.length
    var i = 0
    while (i < n && !xmlNeedsEscape(s.charAt(i))) i += 1
    i
  }

  /** Append `s` JSON-escaped directly into `sb`, starting the per-character work at `from`.
    *
    * Replaces a `String`-returning escaper that allocated a scratch buffer AND a throwaway string for every field NAME and
    * every field VALUE — about twenty short-lived objects for an ordinary five-field payload, since names are escaped on every
    * branch too. Two paths:
    *   - nothing to escape (`from` past the end): one bulk copy, zero allocation. This is the dominant real case.
    *   - otherwise: bulk-copy the clean prefix, then run the original branch list verbatim, in the original order, from `from`.
    */
  private def appendEscapedJson(sb: StringBuilder, s: String, from: Int): Unit = {
    val n = s.length
    if (from >= n) { sb.append(s); () }
    else {
      sb.underlying.append(s, 0, from)
      var i = from
      while (i < n) {
        s.charAt(i) match {
          case '"'          => sb.append("\\\"")
          case '\\'         => sb.append("\\\\")
          case '\n'         => sb.append("\\n")
          case '\r'         => sb.append("\\r")
          case '\t'         => sb.append("\\t")
          case '\b'         => sb.append("\\b")
          case '\f'         => sb.append("\\f")
          // Every remaining escapable character is a C0 control, so the high byte is always `00`.
          // Written digit by digit: the interpolated `f"..."` this replaces built a Formatter, a
          // parsed format string and a String for EVERY control character.
          case c if c < ' ' =>
            sb.append("\\u00").append(HexDigits.charAt((c >> 4) & 0xf)).append(HexDigits.charAt(c & 0xf))
          case c            => sb.append(c)
        }
        i += 1
      }
    }
  }

  private def appendEscapedJson(sb: StringBuilder, s: String): Unit =
    appendEscapedJson(sb, s, jsonEscapeStart(s))

  /** XML counterpart of [[appendEscapedJson]]. */
  private def appendEscapedXml(sb: StringBuilder, s: String, from: Int): Unit = {
    val n = s.length
    if (from >= n) { sb.append(s); () }
    else {
      sb.underlying.append(s, 0, from)
      var i = from
      while (i < n) {
        s.charAt(i) match {
          case '&'  => sb.append("&amp;")
          case '<'  => sb.append("&lt;")
          case '>'  => sb.append("&gt;")
          case '"'  => sb.append("&quot;")
          case '\'' => sb.append("&apos;")
          case c    => sb.append(c)
        }
        i += 1
      }
    }
  }

  private def appendEscapedXml(sb: StringBuilder, s: String): Unit =
    appendEscapedXml(sb, s, xmlEscapeStart(s))

  /** Renders a [[RawValGen]] value into JSON: finite numbers and booleans are emitted raw; `null` and non-finite floating point
    * (`NaN`, `±Infinity` — which have no valid JSON numeric form) become the JSON `null` literal; anything stringy is quoted
    * and JSON-escaped.
    */
  private def appendRawJson(sb: StringBuilder, v: Any): Unit = v match {
    case null                             => sb.append("null")
    case d: Double if !d.isFinite         => sb.append("null")
    case f: Float if !f.isFinite          => sb.append("null")
    case _: java.lang.Number | _: Boolean => sb.append(v.toString)
    case other                            =>
      sb.append('"'); appendEscapedJson(sb, other.toString); sb.append('"')
  }

  /** Renders a [[RawValGen]] value into XML body text: finite numbers and booleans raw; `null` and non-finite floating point
    * (`NaN`, `±Infinity`) as an empty body; anything stringy XML-escaped.
    */
  private def appendRawXml(sb: StringBuilder, v: Any): Unit = v match {
    case null                             => ()
    case d: Double if !d.isFinite         => ()
    case f: Float if !f.isFinite          => ()
    case _: java.lang.Number | _: Boolean => sb.append(v.toString)
    case other                            => appendEscapedXml(sb, other.toString)
  }

  /** Serializes a list of fields to a JSON object string.
    *
    * String values are escaped for JSON-special characters. Gatling EL expressions (`#{var}`) are preserved.
    *
    * @return
    *   JSON string, e.g. `{"id": 1,"name": "test"}`
    */
  def makeJson(fields: List[Field]): String = {
    val sb    = new StringBuilder
    sb.append('{')
    var first = true
    fields.foreach { field =>
      if (!first) sb.append(',')
      first = false
      appendJsonField(sb, field)
    }
    sb.append('}')
    sb.toString
  }

  private def appendJsonField(sb: StringBuilder, field: Field): Unit = field match {
    case Field(name, RawValString(s))       =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": \""); appendEscapedJson(sb, s); sb.append('"')
    case Field(name, RawValGen(s))          =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": "); appendRawJson(sb, s)
    case Field(name, InterpolateStrVal(in)) =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": \"#{").append(in).append("}\"")
    case Field(name, InterpolateGenVal(in)) =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": #{").append(in).append('}')
    case Field(name, ObjectVal(f))          =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": "); appendJsonObject(sb, f)
    case Field(name, ArrayVal(vs))          =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": "); appendJsonArray(sb, vs)
    case Field(name, NullVal)               =>
      sb.append('"'); appendEscapedJson(sb, name); sb.append("\": null")
  }

  private def appendJsonObject(sb: StringBuilder, fields: List[Field]): Unit = {
    sb.append('{')
    var first = true
    fields.foreach { field =>
      if (!first) sb.append(',')
      first = false
      appendJsonField(sb, field)
    }
    sb.append('}')
  }

  private def appendJsonArray(sb: StringBuilder, vals: List[FieldVal]): Unit = {
    sb.append('[')
    var first = true
    vals.foreach { v =>
      if (!first) sb.append(',')
      first = false
      appendJsonValue(sb, v)
    }
    sb.append(']')
  }

  private def appendJsonValue(sb: StringBuilder, v: FieldVal): Unit = v match {
    case RawValString(s)       => sb.append('"'); appendEscapedJson(sb, s); sb.append('"')
    case RawValGen(s)          => appendRawJson(sb, s)
    case InterpolateStrVal(in) => sb.append("\"#{").append(in).append("}\"")
    case InterpolateGenVal(in) => sb.append("#{").append(in).append('}')
    case ObjectVal(f)          => appendJsonObject(sb, f)
    case ArrayVal(vs)          => appendJsonArray(sb, vs)
    case NullVal               => sb.append("null")
  }

  /** Serializes field values to a JSON array string.
    *
    * @return
    *   JSON array string, e.g. `[1,"text",true]`
    */
  def makeArrJson(vals: List[FieldVal]): String = {
    val sb = new StringBuilder
    appendJsonArray(sb, vals)
    sb.toString
  }

  /** Serializes a list of fields to an XML string.
    *
    * String values are escaped for XML-special characters (`&`, `<`, `>`, `"`, `'`). Gatling EL expressions (`#{var}`) are
    * preserved.
    *
    * @return
    *   XML string, e.g. `<id>1</id><name>test</name>`
    */
  def makeXml(fields: List[Field]): String = {
    val sb = new StringBuilder
    fields.foreach(appendXmlField(sb, _))
    sb.toString
  }

  /** The element name is emitted TWICE (open and close tag). The previous code escaped it into a `String` and reused that; here
    * the scan INDEX is computed once and reused instead, so the second emission costs another append rather than another
    * allocation.
    */
  private def appendXmlField(sb: StringBuilder, field: Field): Unit = field match {
    case Field(name, RawValString(s))       =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append('>')
      appendEscapedXml(sb, s)
      sb.append("</"); appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, RawValGen(s))          =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append('>')
      appendRawXml(sb, s)
      sb.append("</"); appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, InterpolateStrVal(in)) =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append(">#{").append(in).append("}</")
      appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, InterpolateGenVal(in)) =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append(">#{").append(in).append("}</")
      appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, ObjectVal(f))          =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append('>')
      f.foreach(appendXmlField(sb, _))
      sb.append("</"); appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, ArrayVal(vs))          =>
      val n = xmlEscapeStart(name)
      sb.append('<'); appendEscapedXml(sb, name, n); sb.append('>')
      appendXmlArray(sb, vs)
      sb.append("</"); appendEscapedXml(sb, name, n); sb.append('>')
    case Field(name, NullVal)               =>
      sb.append('<'); appendEscapedXml(sb, name); sb.append("/>")
  }

  private def appendXmlArray(sb: StringBuilder, vs: List[FieldVal]): Unit =
    vs.foreach {
      case RawValString(s)       => sb.append("<item>"); appendEscapedXml(sb, s); sb.append("</item>")
      case RawValGen(s)          => sb.append("<item>"); appendRawXml(sb, s); sb.append("</item>")
      case InterpolateStrVal(in) => sb.append("<item>#{").append(in).append("}</item>")
      case InterpolateGenVal(in) => sb.append("<item>#{").append(in).append("}</item>")
      case ObjectVal(f)          => sb.append("<item>"); f.foreach(appendXmlField(sb, _)); sb.append("</item>")
      case ArrayVal(vs)          => sb.append("<item>"); appendXmlArray(sb, vs); sb.append("</item>")
      case NullVal               => sb.append("<item/>")
    }

  /** Serializes field values to XML `<item>` elements.
    *
    * @return
    *   XML string of `<item>` elements, e.g. `<item>1</item><item>2</item>`
    */
  def makeXmlArray(vs: List[FieldVal]): String = {
    val sb = new StringBuilder
    appendXmlArray(sb, vs)
    sb.toString
  }

  /** Varargs convenience for `makeXml(List[Field])`. */
  def makeXml(fs: Field*): String = makeXml(fs.toList)

  /** Varargs convenience for `makeJson(List[Field])`. */
  def makeJson(fs: Field*): String = makeJson(fs.toList)
}
