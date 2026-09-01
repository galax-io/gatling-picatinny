# Quickstart: validating this feature

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-09-01

Runnable checks that prove the feature works, in the order the work lands. Each section states what
must be true **before** the change (so the check is not vacuous) and what must be true after.

Contracts referenced rather than repeated: [escaping](contracts/escaping.md),
[cookie parsing](contracts/cookie-parsing.md), [OpenNFR reach](contracts/reach-v0.8.0.md).

---

## Prerequisites

```bash
java -version   # 17 or 21
sbt --version   # project pins 1.13.0; 2.0.6 is the verified secondary
```

No Docker is needed: nothing in this feature touches the container-backed integration layer.

---

## 0. Reproduce the starting condition

Do this first. Several checks below are only meaningful against a recorded failure.

```bash
sbt "Jmh/run -l"
```

Expected **before** the harness fix: fails with `ClassNotFoundException: org.openjdk.jmh.Main`, before
any benchmark executes. If this succeeds on your machine, stop — the premise of User Story 1 does not
hold and the plan needs revisiting.

---

## 1. The benchmark gate runs

```bash
sbt "Jmh/run -l"
```

Expected **after**: exits successfully and lists every benchmark. Each benchmark appears **twice** —
once per declared mode. That is correct JMH behaviour, not a defect.

Allocation profiling, which is what the acceptance criteria are written against:

```bash
sbt "Jmh/run -lprof"
```

Expected: `gc` is listed under supported profilers. `async`, `perf`, `perfnorm`, `perfasm` and
`dtraceasm` are unsupported on a stock development machine here — do not write criteria against them.

Both majors, as the constitution requires:

```bash
sbt --sbt-version 2.0.6 "Jmh/run -l"
```

---

## 2. The published artifact is unaffected

The harness fix must be dependency-neutral. Generate the descriptor and inspect it:

```bash
sbt makePom
```

Expected: the benchmark tooling is declared at the **non-transitive** scope, exactly as before the fix —
not absent, and never at compile scope. Compare against the same file generated before the change; they
should differ only in the version string.

Packaging must not be sensitive to whether benchmarks were run first:

```bash
sbt clean packageBin && cp target/scala-2.13/*.jar /tmp/clean.jar
sbt Jmh/compile packageBin && cp target/scala-2.13/*.jar /tmp/after-bench.jar
unzip -l /tmp/clean.jar | awk '{print $4}' | sort > /tmp/a
unzip -l /tmp/after-bench.jar | awk '{print $4}' | sort > /tmp/b
diff /tmp/a /tmp/b && echo "IDENTICAL"
```

Expected **before** the packaging fix: the two differ by benchmark metadata entries.
Expected **after**: `IDENTICAL`.

---

## 3. Behaviour is frozen before anything is optimized

```bash
sbt "Test/testOnly org.galaxio.gatling.parity.*"
```

Expected: green **at the commit that introduces it**, with no production file yet modified. That is the
point — it pins today's behaviour. It must stay green through every optimization commit without any
expectation being edited.

If it ever goes red, read the failure: it names the offending input and both outcomes. The correct
response is to narrow or drop the optimization, never to adjust the expectation.

---

## 4. The locale fixes

Each of these must **fail at its parent commit and pass at its tip** — check both, or the test proves
nothing.

```bash
sbt "Test/testOnly *TurkishLocale*"
```

Expected after the masking fix: a capitalised secret-bearing configuration key is masked. Exercise all
three decision points — word splitting, the separator-less suffix floor, and an operator-supplied extra
sensitive key.

Expected after the cookie fix: a header spelling the domain attribute in ASCII capitals yields the
domain the server sent, not the fallback. And — deliberately — a header spelling it with the dotted
capital is **no longer** recognised. See [cookie parsing §7](contracts/cookie-parsing.md); that
narrowing is correct and is pinned so it is not later mistaken for a regression.

Full suite, to confirm the locale group has not disturbed anything:

```bash
sbt "Test/testOnly"
```

---

## 5. The optimizations, with their measurement pairs

For each perf commit, record **both** runs. Before, at the parent commit:

```bash
sbt "Jmh/run -prof gc -f 1 -wi 3 -i 5 -rf json -rff before.json .*SyntaxBenchmark.*"
```

After, at the tip:

```bash
sbt "Jmh/run -prof gc -f 1 -wi 3 -i 5 -rf json -rff after.json .*SyntaxBenchmark.*"
```

Compare `gc.alloc.rate.norm` (bytes per operation) per benchmark. Expected: strictly lower on every
case, and throughput no worse. Same procedure for the cookie benchmark.

A change whose pair shows no improvement **does not ship** — its issue closes citing the recording.

Correctness alongside the measurement:

```bash
sbt "Test/testOnly org.galaxio.gatling.templates.* org.galaxio.gatling.storage.* org.galaxio.gatling.parity.*"
```

Expected: green with **no edited expectations**. An optimization commit that modifies an existing
assertion is doing something other than optimizing.

---

## 6. The OpenNFR metric axis

```bash
sbt "Test/testOnly org.galaxio.gatling.assertions.opennfr.*"
```

Expected:

- a requirement naming only a group hierarchy renders one assertion over that group's path;
- the pairing refuses **in both directions** — the group metric under a request selection, and the
  request metric under the group-only selection;
- a document carrying the retired metric name is refused with a message naming the replacement;
- the parity suite compares the two sets **whole**, with nothing subtracted by name, and finds eleven.

Documentation guards move with the code:

```bash
sbt "Test/testOnly org.galaxio.gatling.assertions.opennfr.MigrationTableSpec"
```

Expected: the guard that today asserts the group-only case has *no* equivalent now asserts it *has*
one — and passes. A guard left stale would pass while the docs and the code disagree, which is the
failure this suite exists to prevent.

End to end against a real Gatling run, in the overlay:

```bash
cd examples/scala-sbt-example && sbt "Gatling/testOnly *OpenNfrAssertionsE2E*"
```

---

## 7. Full gate, both majors

```bash
sbt scalafmtCheckAll scalafmtSbtCheck "scalafixAll --check" compile "Test/testOnly"
```

```bash
sbt --sbt-version 2.0.6 scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly"
```

Use `testOnly`, never `test`: on sbt 2 `test` is `testQuick` and passes having run zero tests.

Compatibility, before opening the PR:

```bash
sbt mimaReportBinaryIssues || true
```

Expected: zero new issues against 1.26.0. The `|| true` is required — `mimaReportBinaryIssues` exits
non-zero on findings. Do **not** substitute `mimaFindBinaryIssues`: it returns problems as a value
without printing them, so it looks clean even when it is not (TESTING.md "Static analysis & gates").

Coverage, against the enforced floor of 75% statement / 66% branch:

```bash
sbt clean coverage compile "Test/testOnly" coverageReport
```

Benchmarks must not appear in the report. If a newly added benchmark does, its class name is missing
the naming marker — see [plan.md](plan.md) FR-004.
