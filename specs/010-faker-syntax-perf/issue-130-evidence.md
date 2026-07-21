# Draft closure comment for #130 (NOT posted — maintainer confirmation required)

Proposed disposition: **close as not-planned**, premise no longer holds on Scala 2.13.18.

---

Investigated during spec 010 (`specs/010-faker-syntax-perf`, verification V002/E002).

The issue's premise — `s"$prefix$key"` builds interpolation machinery per entry per
record — is refuted by the compiled bytecode. Disassembly of the production classfile
(`javap -p -c` on `org/galaxio/gatling/feeders/faker/Syntax$FeederOps$.class`, method
`$anonfun$prefixKeys$2`):

```text
30: invokedynamic #362,  0  // InvokeDynamic #16:makeConcatWithConstants:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
```

Scala 2.13 lowers simple `s"..."` interpolations to a single JVM indy string concat
(`StringConcatFactory`) — exactly what `prefix + key` compiles to. There is no
`StringBuilder`/`StringContext` machinery to remove; the suggested fix would be
bytecode-identical. Remaining per-record allocations (result string, tuple, result
map) are inherent to producing a renamed record.

Renamed keys also cannot be pre-built at construction (the issue's other suggestion):
record keys are record-derived and a `Feeder[Any]` carries no homogeneity guarantee;
a first-record memo adds state plus an O(n) comparison per record that costs what it
saves (research R5).

Behavior is additionally locked by a new regression: transform-chain test
(`selectKeys → prefixKeys → withDefaults` incl. `suffixKeys` and empty-affix identity
boundaries) landed with the #129 commit.

Refs: spec 010 harness `verification.md` V002, `evidence.md` E002.
