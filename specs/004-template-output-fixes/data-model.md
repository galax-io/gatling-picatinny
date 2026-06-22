# Phase 1 Data Model: Template Output Fixes (v1.19.0)

This feature touches an existing data model; nothing is added or removed. The entities below
are documented to pin the validation rules each fix introduces. No public type signatures change.

## Entity: `FieldVal` (sealed ADT in `Syntax`)

The ADT of possible template values. Existing variants (unchanged shape):

| Variant | Carries | JSON render | XML render | Affected by |
|---------|---------|-------------|------------|-------------|
| `RawValString(value: String)` | a literal string | quoted + `escapeJson` | `escapeXml` | — (already correct) |
| `RawValGen[+T](value: T)` | any value | **scalar → raw; stringy → quoted + `escapeJson`** | **scalar → raw; stringy → `escapeXml`** | **FR-004** |
| `InterpolateStrVal(name: String)` | an EL var name | `"#{name}"` | `#{name}` | — |
| `InterpolateGenVal[+T](name: T)` | an EL var name | `#{name}` (unquoted) | `#{name}` | — |
| `ObjectVal(f: List[Field])` | nested fields | `{…}` | nested elements | — |
| `ArrayVal(vs: List[FieldVal])` | array elements | `[…]` | `<item>…` | element classification via FR-001/FR-002 |
| `NullVal` | — | `null` | `<tag/>` | — |

### Validation rule changes

- **FR-004 (`RawValGen`)** — emission MUST branch on the wrapped value's runtime type:
  - **Scalar** (`Int`, `Long`, `Short`, `Byte`, `Double`, `Float`, `BigInt`, `BigDecimal`, `Boolean`)
    → emitted raw (unquoted), exactly as today.
  - **Stringy** (everything else) → JSON: quoted + `escapeJson`; XML: `escapeXml`.
  - Invariant: every existing scalar assertion in `SyntaxSpec` (`42`, `true`, `3.14`,
    `9999999999L`) stays byte-identical.

## Entity: `Field`

`case class Field(name: String, fieldVal: FieldVal)` — a named value.

### Validation rule changes

- **FR-003 (XML element name)** — when a `Field` is serialized to XML, `name` MUST pass through
  `escapeXml` at both the open and close tag (parity with the JSON path, which already applies
  `escapeJson(name)`). Plain identifier names render byte-identically to today.

## Entity: `ArrayVal` element classification (the `arr(...)` builder)

`arr[T](vs: T*): ArrayVal` classifies each argument into a `FieldVal`.

### Validation rule changes

- **FR-001 (EL detection)** — a `String` argument is recognized as EL-bearing when it matches the
  widened pattern `#\{([\w.\-]+)\}` (adds `.` and `-` to the previous `\w+`).
- **FR-002 (no truncation)** — an EL-bearing `String` argument MUST be carried so it renders in
  full (whole original string, escaped), NOT reduced to the extracted variable name.
  - Invariant: pure `arr("#{name}")` renders `["#{name}"]` (byte-identical to today); mixed
    `arr("hello #{name}!")` renders `["hello #{name}!"]` (previously `["#{name}"]`).
  - Non-string arguments (`ObjectVal`, `ArrayVal`, `Field`, scalars) classify exactly as today.

## Entity: Template Registry (`Templates.templates`)

`protected lazy val templates: Map[String, Body with Expression[String]]` — built once from the
`templates` classpath resource (filename-without-extension → `ElFileBody`).

### State / validation rule changes

| Classpath state | Today | After (FR-005) |
|-----------------|-------|----------------|
| `templates` resource **missing** (null) | silent empty map → later `NoSuchElementException` from `resolveTemplate` | **fail fast on first dereference**: explicit error naming the missing `templates` directory |
| `templates` resource present, **has files** | map of name → body | unchanged |
| `templates` resource present, **no files** (empty dir) | empty map | empty map (still non-fatal — distinct from missing) |

- Invariant: the registry is `lazy`, so the missing-directory error is raised only when
  `templates` is first dereferenced (i.e. a `postTemplate`/`resolveTemplate` is actually used) —
  mixing in the trait without using a template does not throw.

## Facade mirror: `TemplateSyntax` (Java)

No model change. `field(...)` overloads construct `RawValString`/`RawValGen`; `fieldArr`
delegates to `Syntax.arr`; `makeJson`/`makeXml` delegate to core. The facade inherits all rule
changes above with no facade-side logic. Delegation parity is asserted by a JUnit 5 test.
