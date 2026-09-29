#!/usr/bin/env bash
#
# test-sbt-upgrade-parity-scope.sh -- unit test for the scope decision in
# .github/workflows/sbt-upgrade-parity.yml ("Decide whether this pull request upgrades sbt").
#
# The parity gate is deliberately self-contained (a called workflow cannot reliably check out the
# repository that hosts it), so its logic cannot sit in a script this test could source. The test
# lifts the block between the `# >>> scope-decision` and `# <<< scope-decision` markers out of the
# workflow and runs exactly that text against fixture base/ and head/ trees.
#
# Usage: scripts/test-sbt-upgrade-parity-scope.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/sbt-upgrade-parity.yml"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

[ "$(grep -c '# >>> scope-decision' "$WORKFLOW")" -eq 1 ] || fail "$WORKFLOW must hold exactly one '# >>> scope-decision' marker"
[ "$(grep -c '# <<< scope-decision' "$WORKFLOW")" -eq 1 ] || fail "$WORKFLOW must hold exactly one '# <<< scope-decision' marker"

# The step's `run: |` body sits ten spaces in.
SCOPE="$WORK_DIR/scope.sh"
sed -n '/# >>> scope-decision/,/# <<< scope-decision/p' "$WORKFLOW" | sed -E 's/^ {10}//' > "$SCOPE"
grep -q 'applies=' "$SCOPE" || fail "no scope decision found between the markers"

checks=0
failures=0

# run_case NAME EXPECTED BASE HEAD
#   EXPECTED is the `applies` output the step must write; BASE and HEAD are the text of the two
#   sides' project/build.properties (printf %b escapes allowed) or `-` for "no such file".
run_case() {
  local name="$1" expected="$2" base="$3" head="$4" dir output actual
  checks=$((checks + 1))
  dir="$(mktemp -d "$WORK_DIR/case.XXXXXX")"
  mkdir -p "$dir/base/project" "$dir/head/project"
  if [ "$base" != "-" ]; then printf '%b' "$base" > "$dir/base/project/build.properties"; fi
  if [ "$head" != "-" ]; then printf '%b' "$head" > "$dir/head/project/build.properties"; fi
  : > "$dir/github-output"
  if ! output="$(cd "$dir" && GITHUB_OUTPUT="$dir/github-output" bash "$SCOPE" 2>&1)"; then
    echo "FAIL [$name]: the scope step exited non-zero: $output" >&2
    failures=$((failures + 1))
    return
  fi
  actual="$(cat "$dir/github-output")"
  if [ "$actual" != "applies=$expected" ]; then
    echo "FAIL [$name]: expected applies=$expected, got '$actual'" >&2
    failures=$((failures + 1))
    return
  fi
  # A skipped gate must say so; a running one must not claim otherwise.
  if [ "$expected" = "false" ] && ! grep -q '^::notice title=No sbt upgrade::' <<< "$output"; then
    echo "FAIL [$name]: skipped without the notice" >&2
    failures=$((failures + 1))
    return
  fi
  if [ "$expected" = "true" ] && grep -q '^::notice' <<< "$output"; then
    echo "FAIL [$name]: runs the gate but printed the skip notice" >&2
    failures=$((failures + 1))
    return
  fi
  echo "ok   [$name] applies=$expected"
}

# Skipped: the pull request leaves sbt.version alone (a dependency bump, a feature).
run_case "unchanged pin"                       false 'sbt.version=2.0.9\n'                    'sbt.version=2.0.9\n'
run_case "unchanged pin, comment edited"       false '# pinned\nsbt.version=2.0.9\n'          '# pinned for the gate\nsbt.version=2.0.9\n'
run_case "unchanged pin, spacing and CRLF"     false 'sbt.version = 2.0.9\r\n'                'sbt.version:2.0.9\n'
run_case "unchanged pin, last assignment wins" false 'sbt.version=1.13.0\nsbt.version=2.0.9\n' 'sbt.version=2.0.9\n'

# Runs: an upgrade in any direction, or a side the step cannot read (when in doubt the gate runs).
run_case "upgrade sbt 1 to sbt 2"              true  'sbt.version=1.13.0\n'                   'sbt.version=2.0.9\n'
run_case "upgrade within sbt 2"                true  'sbt.version=2.0.9\n'                    'sbt.version=2.1.0\n'
run_case "downgrade sbt 2 to sbt 1"            true  'sbt.version=2.0.9\n'                    'sbt.version=1.13.0\n'
run_case "head drops the pin"                  true  'sbt.version=2.0.9\n'                    'foo=bar\n'
run_case "base has no pin"                     true  'foo=bar\n'                              'sbt.version=2.0.9\n'
run_case "neither side has a pin"              true  'foo=bar\n'                              'foo=bar\n'
run_case "base has no build.properties"        true  -                                        'sbt.version=2.0.9\n'
run_case "head has no build.properties"        true  'sbt.version=2.0.9\n'                    -
run_case "neither side has build.properties"   true  -                                        -

echo "$checks checks, $failures failed"
[ "$failures" -eq 0 ]
