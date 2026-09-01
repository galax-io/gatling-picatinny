package org.galaxio.gatling.parity

import org.galaxio.gatling.templates.Syntax._

/** A VERBATIM, FROZEN copy of the body-assembly and escaping region of `templates/Syntax.scala`.
  *
  * It exists so the #126 rewrite can be checked against what the code did BEFORE it, over generated inputs rather than the
  * handful of cases someone thought to write a test for.
  *
  * DO NOT EDIT THE COPIED REGION. If a parity suite goes red, the live code changed behaviour — that is the finding, and the
  * response is to narrow or drop the optimization, never to adjust this file. There is no automated guard against drift: a
  * checksum test would just be another thing to regenerate. Any diff here is a hard review stop.
  *
  * Reproduce with: {{{ git show 7b44616ozen at 7b44616 (2026-09-01), which is AFTER the locale fixes and BEFORE any
  * optimization. Only the object wrapper and this scaladoc are added; the region itself is byte-for-byte the original.
  */
private[parity] object FrozenSyntaxReference {

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
