# Contract: what this library exposes

**Feature**: `013-opennfr-assertions`. Experimental surface — outside the binary-compatibility
guarantee the rest of the library keeps (spec, Assumptions). The deprecated NFR-YAML surface is
unchanged and keeps its guarantees in full.

## Scala

**One entry point**, taking the path of an OpenNFR document and returning the assertions it denotes,
for use in a simulation's `assertions(...)`. It resolves the Gatling configuration from the implicit
a running simulation provides, exactly as the deprecated path does.

**A test seam** taking the configuration explicitly, so the core is unit-testable outside a running
simulation — the same seam shape `assertionsFrom` already uses, for the same reason.

**A pure form** returning either every reason the document could not be rendered, or the assertions.
The entry point is this, folded: a `Left` becomes one exception, a `Right` becomes the list.

## Java / Kotlin

The same entry point, returning the Java DSL's assertion type. Every rule is decided in the Scala
core; the facade re-issues the resolved decision and adds no decision of its own (R2).

## The refusal contract

A refusal is **total and loud**. There is no partial success: a document with one unrenderable
predicate produces no assertions at all, because a simulation that silently ran nine of ten checks is
the failure this feature exists to prevent.

A refusal carries **every** reason, not the first. Each names:

1. the requirement it came from, by its `name`;
2. the predicate, by its `name` where set and its `aggregation` otherwise — the format's own identity rule;
3. the reason, in the words of the reach row that refused it.

A document that cannot be read, or that is not an OpenNFR document at all, fails the same way, naming
the path.

## What is deliberately not exposed

- **No partial or lenient mode.** See above.
- **No way to override a reach rule.** Widening what renders is an upstream change, not a library flag — the whole point of adopting a tool-agnostic format.
- **No result or report document.** Nothing upstream produces one.
- **No classpath or URL loading.** A local path, as the deprecated entry point takes (spec, Assumptions).
