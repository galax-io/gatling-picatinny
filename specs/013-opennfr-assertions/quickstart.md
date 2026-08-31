# Quickstart — validating OpenNFR assertions

**Feature**: `013-opennfr-assertions` · **Date**: 2026-08-30

How to prove the feature works, in the order the proofs get stronger. Every command runs from the
repository root. Nothing here is implementation — see [`data-model.md`](data-model.md) and
[`contracts/`](contracts/) for what the checks are checking.

## Prerequisites

Nothing beyond the existing build. Parsing needs no new dependency (`circe-yaml` is already present).
**Check 5 requires the R1 dependency to have been authorized**; until then it is expected to be absent
and the rest still hold.

---

## 1. The unit suite

```bash
sbt "Test/testOnly org.galaxio.gatling.assertions.opennfr.*"
```

**Expected**: green, and the suite contains at least one test per functional requirement in the
plan's Test Model table. A row without a test is a gap in the feature, not in this document.

## 2. Parity against the oracle — the strongest check

```bash
sbt "Test/testOnly *OpenNfrAssertionsSpec -- -z parity"
```

**Expected**: the translated fixture and `src/test/resources/nfr.yml` build **equal** assertion sets
over ten assertions, compared as sets. The eleventh — the group-only scope — is excluded by name, and
a second test asserts the *reason* it is excluded.

**If this fails, the feature is wrong**, not the test: it is the only check that compares against a
known-good producer of the exact values Gatling evaluates.

## 3. The deprecated path is untouched

```bash
sbt "Test/testOnly org.galaxio.gatling.assertions.AssertionsBuilderSpec"
sbt "Test/testOnly org.galaxio.gatling.javaapi.assertions.*"
```

**Expected**: green, unmodified. Eleven assertions from `nfr.yml`, the group-only one included.

## 4. Refusals are total, and complete

```bash
sbt "Test/testOnly *OpenNfrAssertionsSpec -- -z refus"
```

**Expected**: for a document carrying three unrelated defects, exactly three reasons, each naming its
requirement and predicate. For a document carrying one unrenderable predicate among nine good ones,
**zero** assertions — a partial render is the silent-green failure this feature exists to prevent.

## 5. Schema validation and the pinned copy *(after R1 is authorized)*

```bash
sbt "Test/testOnly *OpenNfrSchemaSpec"
```

**Expected**: every document in the upstream corpus validates; a document the schema rejects — an
unknown key, an empty `criteria`, a malformed `name` — is refused here too; and the vendored schema is
byte-identical to the recorded upstream release. The last check must fail even when a local edit makes
the schema *more* permissive.

## 6. The whole gate

```bash
sbt scalafixAll scalafmtAll
sbt "scalafixAll --check" scalafmtCheckAll scalafmtSbtCheck compile "Test/testOnly"
```

Then on the secondary major, per AGENTS.md:

```bash
sbt --sbt-version 2.0.6 compile "Test/testOnly"
```

**Expected**: green on both. Coverage at or above the enforced floor:

```bash
sbt clean coverage "Test/testOnly" coverageOff coverageReport
```

## 7. End to end, in a real run

In the `examples/` overlay, a simulation drives the picatinny DSL over real HTTP against WireMock and
takes its assertions from an OpenNFR document:

```bash
sbt 'Gatling/testOnly *'
```

**Expected**: the run's verdict is the one the document states — a passing document passes, and a
document whose threshold the run breaches fails the build. This is the only check that exercises the
Gatling runtime for real rather than describing it, and it is what proves the assertions a document
denotes are the ones a run actually evaluates.

---

## What none of this checks

- **That the format is right.** Thirteen issues are open upstream; one (`opennfr#74`) would change what this library accepts. The rest are the format's own hygiene and are not on this feature's path.
- **That a group-scoped requirement can be written.** It cannot, permanently, and `contracts/reach.md` says why.
