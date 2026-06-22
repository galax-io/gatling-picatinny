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

  private def escapeJson(s: String): String = {
    val sb = new StringBuilder(s.length)
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'          => sb.append("\\\"")
        case '\\'         => sb.append("\\\\")
        case '\n'         => sb.append("\\n")
        case '\r'         => sb.append("\\r")
        case '\t'         => sb.append("\\t")
        case '\b'         => sb.append("\\b")
        case '\f'         => sb.append("\\f")
        case c if c < ' ' => sb.append(f"\\u${c.toInt}%04x")
        case c            => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }

  private def escapeXml(s: String): String = {
    val sb = new StringBuilder(s.length)
    var i  = 0
    while (i < s.length) {
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
    sb.toString
  }

  /** Renders a [[RawValGen]] value into JSON: finite numbers and booleans are emitted raw; `null` and non-finite floating point
    * (`NaN`, `±Infinity` — which have no valid JSON numeric form) become the JSON `null` literal; anything stringy is quoted
    * and JSON-escaped.
    */
  private def appendRawJson(sb: StringBuilder, v: Any): Unit = v match {
    case null                             => sb.append("null")
    case d: Double if !d.isFinite         => sb.append("null")
    case f: Float if !f.isFinite          => sb.append("null")
    case _: java.lang.Number | _: Boolean => sb.append(v.toString)
    case other                            => sb.append('"').append(escapeJson(other.toString)).append('"')
  }

  /** Renders a [[RawValGen]] value into XML body text: finite numbers and booleans raw; `null` and non-finite floating point
    * (`NaN`, `±Infinity`) as an empty body; anything stringy XML-escaped.
    */
  private def appendRawXml(sb: StringBuilder, v: Any): Unit = v match {
    case null                             => ()
    case d: Double if !d.isFinite         => ()
    case f: Float if !f.isFinite          => ()
    case _: java.lang.Number | _: Boolean => sb.append(v.toString)
    case other                            => sb.append(escapeXml(other.toString))
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
      sb.append('"').append(escapeJson(name)).append("\": \"").append(escapeJson(s)).append('"')
    case Field(name, RawValGen(s))          =>
      sb.append('"').append(escapeJson(name)).append("\": "); appendRawJson(sb, s)
    case Field(name, InterpolateStrVal(in)) =>
      sb.append('"').append(escapeJson(name)).append("\": \"#{").append(in).append("}\"")
    case Field(name, InterpolateGenVal(in)) =>
      sb.append('"').append(escapeJson(name)).append("\": #{").append(in).append('}')
    case Field(name, ObjectVal(f))          =>
      sb.append('"').append(escapeJson(name)).append("\": "); appendJsonObject(sb, f)
    case Field(name, ArrayVal(vs))          =>
      sb.append('"').append(escapeJson(name)).append("\": "); appendJsonArray(sb, vs)
    case Field(name, NullVal)               =>
      sb.append('"').append(escapeJson(name)).append("\": null")
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
    case RawValString(s)       => sb.append('"').append(escapeJson(s)).append('"')
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

  private def appendXmlField(sb: StringBuilder, field: Field): Unit = field match {
    case Field(name, RawValString(s))       =>
      val n = escapeXml(name)
      sb.append('<').append(n).append('>').append(escapeXml(s)).append("</").append(n).append('>')
    case Field(name, RawValGen(s))          =>
      val n = escapeXml(name)
      sb.append('<').append(n).append('>'); appendRawXml(sb, s); sb.append("</").append(n).append('>')
    case Field(name, InterpolateStrVal(in)) =>
      val n = escapeXml(name)
      sb.append('<').append(n).append(">#{").append(in).append("}</").append(n).append('>')
    case Field(name, InterpolateGenVal(in)) =>
      val n = escapeXml(name)
      sb.append('<').append(n).append(">#{").append(in).append("}</").append(n).append('>')
    case Field(name, ObjectVal(f))          =>
      val n = escapeXml(name)
      sb.append('<').append(n).append('>'); f.foreach(appendXmlField(sb, _)); sb.append("</").append(n).append('>')
    case Field(name, ArrayVal(vs))          =>
      val n = escapeXml(name)
      sb.append('<').append(n).append('>'); appendXmlArray(sb, vs); sb.append("</").append(n).append('>')
    case Field(name, NullVal)               =>
      sb.append('<').append(escapeXml(name)).append("/>")
  }

  private def appendXmlArray(sb: StringBuilder, vs: List[FieldVal]): Unit =
    vs.foreach {
      case RawValString(s)       => sb.append("<item>").append(escapeXml(s)).append("</item>")
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
