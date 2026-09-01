# T003/T013 — POM baseline for FR-027
# Recorded: 2026-09-01

The generated POM is dynver-stamped and regenerable in seconds (`sbt makePom`), so the
finding is recorded here rather than the file, which would go stale on the next dependency
or version change.

Verified before and after the harness fix, normalising only the <version> string:
  - the two POMs are byte-identical (same md5)
  - org.openjdk.jmh appears in BOTH, at <scope>provided</scope>:
      jmh-core                  scope=provided
      jmh-generator-bytecode    scope=provided
      jmh-generator-reflection  scope=provided

So the fix is dependency-NEUTRAL, not dependency-cleaning. jmh is never at compile scope,
hence never transitive to consumers — which is the property spec US1 scenario 7 asserts
after its correction. Regenerate with: sbt makePom
