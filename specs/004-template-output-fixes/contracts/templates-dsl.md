# Contract: Templates DSL Public Rendering Surface (v1.19.0)

The templates DSL exposes its contract as the **exact bytes** produced by `makeJson`, `makeXml`,
`makeArrJson`, `makeXmlArray`, `arr`, and the `Templates` trait's `postTemplate`. This document
records the input → exact-output contract for the cases this milestone changes. Rows marked
**unchanged** are regression guards (must stay byte-identical to v1.18.x).

Method signatures are **not** changing. Only output bytes for the affected inputs change.

## `arr(...)` element classification + array rendering

| Input | v1.18.x output | v1.19.0 output | Req |
|-------|----------------|----------------|-----|
| `makeArrJson(arr("#{name}").vs)` | `["#{name}"]` | `["#{name}"]` (**unchanged**) | FR-002 regression |
| `makeArrJson(arr("#{user.id}").vs)` | `["#{user.id}"]` | `["#{user.id}"]` (**unchanged bytes**; now classified as EL) | FR-001 |
| `makeArrJson(arr("#{tenant-name}").vs)` | `["#{tenant-name}"]` | `["#{tenant-name}"]` (**unchanged bytes**; now classified as EL) | FR-001 |
| `makeArrJson(arr("hello #{name}!").vs)` | `["#{name}"]` ⚠ truncated | `["hello #{name}!"]` ✅ | FR-002 |
| `makeArrJson(arr("plain").vs)` | `["plain"]` | `["plain"]` (**unchanged**) | FR-002 regression |
| `makeArrJson(arr("#{0}").vs)` | `["#{0}"]` | `["#{0}"]` (**unchanged** — `\w` already matches digits) | FR-001 edge `#{0}` |
| `makeArrJson(arr("#{a} #{b}").vs)` | `["#{a}"]` ⚠ truncated | `["#{a} #{b}"]` ✅ full, multi-EL | FR-002 boundary |
| `makeArrJson(arr("").vs)` | `[""]` | `[""]` (**unchanged**) | FR-002 empty-string edge |
| `makeArrJson(arr("say \"#{x}\"").vs)` | `["#{x}"]` ⚠ dropped + unescaped | `["say \"#{x}\""]` ✅ escaped, full | FR-002 |

## `RawValGen` value rendering (JSON + XML)

| Input | v1.18.x output | v1.19.0 output | Req |
|-------|----------------|----------------|-----|
| `makeJson("n" - 42)` | `{"n": 42}` | `{"n": 42}` (**unchanged**) | FR-004 regression |
| `makeJson("b" - true)` | `{"b": true}` | `{"b": true}` (**unchanged**) | FR-004 regression |
| `makeJson("d" - 3.14)` | `{"d": 3.14}` | `{"d": 3.14}` (**unchanged**) | FR-004 regression |
| `makeArrJson(List(RawValGen("a\"b")))` | `["a"b"]` ⚠ invalid JSON | `["a\"b"]` ✅ quoted + escaped | FR-004 |
| `makeXmlArray(List(RawValGen("<x>")))` | `<item><x></item>` ⚠ injection | `<item>&lt;x&gt;</item>` ✅ escaped | FR-004 |
| `makeXml("k" - someStringRawValGen)` | raw string into element body | `escapeXml`-ed body | FR-004 |
| `makeArrJson(List(RawValGen("")))` | `[""]` (empty raw → malformed) | `[""]` ✅ quoted empty | FR-004 empty-string edge |
| `makeArrJson(List(RawValGen(null)))` | `[null]` (raw `null` literal) / NPE risk under naive escape | `[null]` ✅ explicit `null`, no NPE | FR-004 null edge |
| `makeArrJson(List(RawValGen(Double.NaN)))` | `[NaN]` ⚠ invalid JSON | `[null]` ✅ | FR-004 non-finite |
| `makeArrJson(List(RawValGen(Double.PositiveInfinity)))` | `[Infinity]` ⚠ invalid JSON | `[null]` ✅ | FR-004 non-finite |
| `makeXmlArray(List(RawValGen(Double.NaN)))` | `<item>NaN</item>` | `<item></item>` ✅ empty body | FR-004 non-finite |

> Scalar detection set: `Int, Long, Short, Byte, Double, Float, BigInt, BigDecimal, Boolean` →
> raw. `null` and non-finite floating point (`NaN`, `±Infinity`, which have no valid JSON numeric
> form) → `null` (JSON) / empty body (XML), guarded against NPE. Everything else → stringy
> (escaped; quoted in JSON).

## XML element-name escaping

| Input | v1.18.x output | v1.19.0 output | Req |
|-------|----------------|----------------|-----|
| `makeXml("name" - "foo")` | `<name>foo</name>` | `<name>foo</name>` (**unchanged**) | FR-003 regression |
| `makeXml(Field("a<b", RawValString("v")))` | `<a<b>v</a<b>` ⚠ malformed/injectable | `<a&lt;b>v</a&lt;b>` ✅ name escaped | FR-003 |
| existing JSON name escaping `makeJson(...)` | already `escapeJson(name)` | unchanged | FR-003 parity reference |

## `Templates` trait — template registry lifecycle

| Classpath state | v1.18.x | v1.19.0 | Req |
|-----------------|---------|---------|-----|
| `templates` resource missing (null) | empty map → later `NoSuchElementException` deep in scenario | **explicit error on first registry dereference**, message names the missing `templates` directory | FR-005 |
| `templates` resource present with files | name→body map | unchanged | FR-005 regression |
| `templates` resource present, empty dir | empty map | empty map (**unchanged** — not an error) | FR-005 boundary |
| trait mixed in but `templates` never dereferenced | no effect (lazy) | no effect (lazy) — error only on first use | FR-005 invariant |

## Java facade (`TemplateSyntax`) — delegation contract

| Facade call | Delegates to | Contract |
|-------------|--------------|----------|
| `TemplateSyntax.makeJson(fields…)` | `Syntax.makeJson` | output byte-identical to core |
| `TemplateSyntax.makeXml(fields…)` | `Syntax.makeXml` | output byte-identical to core (incl. FR-003/FR-004 escaping) |
| `TemplateSyntax.fieldArr(name, values…)` | `Syntax.arr` | element classification/rendering identical to core (incl. FR-001/FR-002) |
| `TemplateSyntax.field(name, int/long/double/boolean)` | `RawValGen` scalar | stays raw (FR-004 scalar branch) |

**No facade-side logic** is permitted (Constitution I). The delegation test asserts facade
output equals core output for an escaped-value case.
