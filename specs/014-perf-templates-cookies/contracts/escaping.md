# Contract: body-text escaping

**Source of truth**: the behaviour of the two escape helpers in `templates/Syntax.scala` as they stand
at the parent of this feature's first commit. The rewrite (#126) must change **nothing** in §1–§4.

---

## 1. Where escaping applies

Escaping runs on **field names as well as values**, on every branch that emits either. This is the half
a value-only rewrite silently misses, and it is why an ordinary five-field object of plain strings
currently pays about twenty short-lived objects rather than ten.

| Emitted | JSON | XML |
|---|---|---|
| field name | escaped | escaped, and reused for the closing tag |
| literal string value | escaped | escaped |
| array element that is a string | escaped | escaped |
| expression reference | **not** escaped — emitted verbatim inside its delimiters | **not** escaped |
| number, boolean, null | **not** escaped | **not** escaped |

An expression reference passing through unescaped is a deliberate property of the DSL, not an
oversight: escaping it would break interpolation at run time.

## 2. The JSON escape set

Exactly these characters are transformed. Everything else, including every character above the
printable boundary, is emitted unchanged.

| Input | Output |
|---|---|
| quotation mark | backslash, quotation mark |
| backslash | two backslashes |
| newline | backslash, `n` |
| carriage return | backslash, `r` |
| tab | backslash, `t` |
| backspace | backslash, `b` |
| form feed | backslash, `f` |
| any other character below the printable boundary | backslash, `u`, then **four lowercase hexadecimal digits, zero-padded** |

**Branch order is part of the contract.** The five characters with dedicated short escapes all sit
below the printable boundary, so they must be matched *before* the catch-all that produces the
hexadecimal form. Reordering them silently changes output.

## 3. The XML escape set

| Input | Output |
|---|---|
| ampersand | `&amp;` |
| less-than | `&lt;` |
| greater-than | `&gt;` |
| quotation mark | `&quot;` |
| apostrophe | `&apos;` |

Everything else is emitted unchanged. Notably, control characters are **not** escaped in XML — only the
five above are. A rewrite that unifies the two escapers would introduce a difference here.

## 4. Characters outside the escape sets

- Empty string produces empty output.
- Surrogate halves pass through unchanged, individually. The current implementation walks the string one
  code unit at a time and never inspects pairs, so an unpaired surrogate survives as-is; any rewrite
  must behave the same. Appending a run of characters in bulk is observably identical to appending them
  one at a time, so a fast path does not change this.

## 5. What the rewrite changes — none of it observable

| Before | After |
|---|---|
| each name and each value gets its own scratch buffer, then a copy out of it into the body being assembled | escaped text is written **directly** into the body being assembled |
| text with nothing to escape still pays a buffer and a copy | a scan finds the first character needing escape; when there is none, the whole string is appended in one bulk copy with no allocation |
| a control character builds its escape through general-purpose text formatting, allocating per character | the four hexadecimal digits are written directly |
| two helpers returning strings | helpers that append; no caller needed a string — the one caller that used a name twice keeps an index instead |

Measured effect: 50–93% less allocation depending on fixture. Output: unchanged, character for
character.

## 6. The invariant a future change can break silently

The fast path is guarded by a predicate that must agree exactly with the branch list. Today they agree
for a reason that the branch list does not restate: the five short-escape characters are all below the
printable boundary, so a single "below the boundary" test covers them.

**If anyone later adds a dedicated escape for a printable character** — the forward slash being the
usual candidate — **to the branch list without adding it to the predicate, the fast path will skip over
it and emit it raw.** Silent corruption, and no test in the repository today would catch it.

Two mitigations, both required:

1. The predicate carries a comment tying it to the branch list.
2. The equivalence suite **enumerates** the escape set rather than sampling it, so a character present
   in one and not the other fails immediately.

## 7. Benchmark obligations

The existing body-assembly benchmark cannot see a regression in this contract: not one of its fixture
strings contains any character from §2 or §3. Before/after numbers for #126 are meaningless until the
fixture set includes:

- at least one JSON case exercising the full §2 set, including a control character;
- at least one XML case exercising the full §3 set;
- the pre-existing escape-free cases, retained — the fast path needs measuring too;
- a case whose only escapable character is at the end, so the prefix copy is exercised.
