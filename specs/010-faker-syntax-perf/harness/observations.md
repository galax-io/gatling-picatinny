# Compressed Observations

Append-only. Each entry ≤ 3 lines: what was done, what it yielded, what it
duplicates (if anything). Never paste raw tool output here.

<!-- Format: - [O-001] (action summary) → outcome; dup-of O-xxx if applicable -->
- [O-001] jshell sweep: BigInt vs Long-span classification, 2,000,078 pairs → 0 mismatches, bounds exact (V001 verified).
- [O-002] jshell locale: U+0130 String-lower = 2 codepoints, Character-lower = 1; tr 'I'→0x131 → R3 justified (V003).
- [O-003] javap on Syntax$FeederOps$ `$anonfun$prefixKeys$2` → makeConcatWithConstants indy; #130 premise refuted (V002).
- [O-004] git ancestry: 23f2243 (#300) NOT ancestor of HEAD; merged to origin/main ~43 min post-branch-cut → rebase prerequisite (V010 refuted plan claim).
- [O-005] facade grep: FakerApi one-line delegates only → no second fix site (V004).
- [O-006] build.sbt: JmhPlugin, coverage 75/66 + benchmark exclusions, MiMa 1.23.0 → gates real (V011, V012).
- [O-007] source re-reads: CPF stella (V006), selectKeys hoist (V008), withDefaults test lock (V009), substrate fan-out (V005), assembly sites (V013), no Faker.java (V007).
- [O-008] user challenged ipv6 plan (hexString) → read impl: shared scala.util.Random + Iterator chain; hexString NOT @deprecated but legacy surface; R4/spec/plan amended to inline TLR hex (V014).
