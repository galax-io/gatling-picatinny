# Contract: `Set-Cookie` parsing

**Source of truth**: the behaviour of `storage/CookieParser.scala` as it stands at the parent of this
feature's first commit. This document transcribes it so a rewrite can be checked against a written
statement rather than against a reading of the code it is replacing.

Two changes touch this path and they are deliberately **separate commits** with different obligations:

| Commit | Obligation |
|---|---|
| single-pass fold (#137) | changes **nothing** observable |
| locale fix (#333) | changes **exactly one** thing, stated in §7 |

Everything in §1–§6 must hold identically before and after both.

---

## 1. Splitting a header into cookies

- The raw value is split on the newline character. Trailing empty results are discarded, so a trailing
  newline adds no cookie.
- Each resulting line is trimmed before anything else. A carriage return at a line end is therefore
  absorbed by that trim, so CRLF-delimited input behaves as LF-delimited input.
- A line that is empty after trimming yields no cookie and no diagnostic.
- Cookies appear in the output in the order their lines appear in the input.

## 2. Splitting a cookie into parts

- The trimmed line is split on the semicolon. **Trailing empty results are discarded**, so `a=1;` and
  `a=1;;` both yield exactly one part.
- The first part is the name/value pair. The remainder are attributes.
- **A line consisting only of separators has no first part and raises an error.** This is undocumented
  and untested today, and it is *not* a rewrite's business to improve: a defensive rewrite that returns
  an empty result instead has changed behaviour. Measured at 408 divergences per 20,082 generated
  inputs. Preserve the raise; changing it is a separate decision.

## 3. Name and value

- The first part is split on the first equals sign only; everything after it is the value, so a value
  may itself contain equals signs.
- If there is no equals sign, the line yields **no cookie** and no diagnostic.
- If there is an equals sign, the pair is accepted even when the value is empty (`a=`).
- Name and value are each trimmed **after** the split. Both trims are load-bearing: omitting the name
  trim diverges on 12,150 of 20,082 generated inputs, omitting the value trim on 7,601.

## 4. Attributes

- Each attribute is trimmed, then split on its first equals sign into a key and a value.
- An attribute with no equals sign has an empty value.
- The key is compared **case-insensitively** (see §7 for how).
- The attribute value is trimmed.
- An unrecognised attribute is ignored silently.

Recognised keys, and what each contributes:

| Key | Contributes | Absent |
|---|---|---|
| `domain` | the cookie's domain | falls back to the caller-supplied default |
| `path` | the cookie's path | stays absent |
| `max-age` | the cookie's lifetime, parsed as a whole number | stays absent |
| `secure` | sets the secure flag | flag stays false |
| `httponly` | sets the http-only flag | flag stays false |

## 5. Duplicates and emptiness — the traps

- **Last occurrence wins** for `domain`, `path` and `max-age`. This follows from building a map today;
  a fold must reproduce it by *replacing* the slot on each occurrence.
- **Flags are sticky.** They are decided by the attribute's *presence*, never by its value, so `secure`
  and `secure=false` both set the flag, and a later occurrence cannot clear it.
- **An empty attribute value is not the same as an absent attribute.** `Domain=` yields an empty
  domain; a missing `Domain` yields the caller's default. Conflating the two is the most likely
  rewrite error and is invisible to every test that exists today.

## 6. The lifetime warning

- A `max-age` whose value does not parse as a whole number yields **no lifetime** and **exactly one**
  warning, naming the offending value and the cookie's name.
- Where `max-age` repeats, last-wins applies **first**: the warning is about the surviving occurrence
  only. A fold that parses inside the loop warns once per occurrence and is wrong.
- An empty `max-age` value is unparseable and warns like any other unparseable value.
- A negative value parses and does **not** warn.
- No `max-age` attribute at all produces no warning.

## 7. Attribute-name case folding — the one deliberate change

**Today**: the key is lowered using the host's default locale.

**After**: the key is lowered using a locale-independent rule.

Consequences, both intended:

| Spelling | Today, Turkish host | After | Verdict |
|---|---|---|---|
| `Domain`, `domain`, `DoMaIn` (ASCII) | recognised | recognised | unchanged |
| `DOMAIN` (ASCII capitals) | **not** recognised — the capital `I` lowers to a dotless letter, the lookup misses, and the cookie silently takes the default domain with no diagnostic | recognised | **the fix** |
| `DOMAİN` (dotted capital, non-ASCII) | recognised — that host's rule folds it onto the ASCII name | **not** recognised | **deliberate narrowing** |

The narrowing is correct and must be pinned by a test so it is not later mistaken for a regression.
Cookie attribute names are ASCII; a dotted capital is not a valid spelling, and it is recognised today
only by accident and only on one family of hosts. Accepting it *is* the locale dependence being
removed. Measured at 5,035 divergences per 20,082 inputs under a Turkish host.

`DOMAIN` is the only reachable case of the widening half: of the five recognised keys it is the only one
containing an ASCII capital `I`.

## 8. What a conforming rewrite may not do

- Introduce index arithmetic over the raw string. The fold stays within the project's string-handling
  style rules; a character-scan parser would need a recorded exemption and buys little over the fold.
- Replace the first-part access with a total one — see §2.
- Parse the lifetime inside the accumulation — see §6.
- Derive a flag from an attribute's value — see §5.
- Drop either trim — see §3.
