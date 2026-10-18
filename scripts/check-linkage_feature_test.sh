#!/usr/bin/env bash
#
# check-linkage_feature_test.sh — tests for the feature rule of scripts/check-linkage.sh
# and for the local behaviour it must keep (see AGENTS.md "Feature milestones").
#
# Hermetic: a stub `gh` sits first on PATH and answers, from fixture files, the
# only calls the script makes, so there is no network and no credentials.
#
# Why a second test file: scripts/check-linkage_test.sh was copied from the bootstrap
# template and asserts the template's script (Closes owner/repo#N links, technical PRs,
# base-branch checks), which this repo's script does not implement; it fails on its own
# rules and is not wired into CI. This file tests what this repo's script does.

# Every `check` condition is a $(... && echo 0 || echo 1) that yields exactly one
# token, so the unquoted substitution cannot split.
# shellcheck disable=SC2046
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/check-linkage.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

command -v jq >/dev/null 2>&1 || { echo "error: 'jq' not found on PATH" >&2; exit 2; }

pass=0; fail=0
ok()  { printf '  ✓ %s\n' "$1"; pass=$((pass + 1)); }
bad() { printf '  ✗ %s\n     %s\n' "$1" "$2"; fail=$((fail + 1)); }
check() { # description, condition-exit-code, detail
  if [ "$2" -eq 0 ]; then ok "$1"; else bad "$1" "$3"; fi
}

# ---- the stub `gh` ---------------------------------------------------------------
# Fixtures live in $FIXTURES: items.ndjson (the milestone's issues and PRs as REST
# returns them), pr-<N>.json and issue-<owner>_<repo>-<N>.json. A call it does not
# know is logged to unexpected.log, so a test sees that the script reached for
# something else.
bin="$tmp/bin"; mkdir -p "$bin"
cat > "$bin/gh" <<'STUB'
#!/usr/bin/env bash
fx="${FIXTURES:?}"
unexpected() { echo "gh $*" >> "$fx/unexpected.log"; echo "stub gh: unexpected call: gh $*" >&2; exit 99; }
all="$*"
echo "GH_TOKEN=${GH_TOKEN:-none} gh $*" >> "$fx/calls.log"
# With a backlog-hidden fixture the backlog is private: only GH_TOKEN=backlog-secret reads it.
if [ -f "$fx/backlog-hidden" ]; then
  case "$all" in
    *"$BACKLOG_REPO"*) [ "${GH_TOKEN:-}" = backlog-secret ] || { echo "HTTP 404: Not Found" >&2; exit 1; } ;;
  esac
fi
cmd="${1:-}"; [ $# -gt 0 ] && shift
expr=""; paginate=0; repo=""; positional=()
while [ $# -gt 0 ]; do
  case "$1" in
    --jq|-q)            shift; expr="${1:-}" ;;
    --paginate|--slurp) paginate=1 ;;
    --repo)             shift; repo="${1:-}" ;;
    --json)             shift ;;
    *)                  positional+=("$1") ;;
  esac
  shift
done
reply() { # file — the fixture, filtered through the --jq/-q expression when given
  if [ -n "$expr" ]; then jq -r "$expr" "$1"; else cat "$1"; fi
}
case "$cmd" in
  api)
    path="${positional[0]:-}"
    case "$path" in
      "repos/$REPO/milestones?state=open&per_page=100")
        echo '[{"number":8,"title":"v0.8.0"}]' | jq -r "${expr:-.}" ;;
      "repos/$REPO/milestones?state=all&per_page=100")
        echo '[{"number":7,"title":"v0.7.0"},{"number":8,"title":"v0.8.0"}]' ;;
      "repos/$BACKLOG_REPO")
        echo '{}' ;;
      "repos/$REPO")
        echo '{"default_branch":"main"}' | jq -r "${expr:-.}" ;;
      "repos/$REPO/milestones/8")
        echo '{"title":"v0.8.0","state":"open"}' ;;
      "repos/$REPO/issues?milestone=8&state=all&per_page=100")
        [ "$paginate" = 1 ] || unexpected "$all"
        jq -sc '[.]' "$fx/items.ndjson" ;;   # --paginate --slurp: an array of pages
      *) unexpected "$all" ;;
    esac ;;
  pr)
    [ "${positional[0]:-}" = view ] && [ "$repo" = "$REPO" ] && [ -f "$fx/pr-${positional[1]:-}.json" ] || unexpected "$all"
    reply "$fx/pr-${positional[1]}.json" ;;
  issue)
    f="$fx/issue-${repo//\//_}-${positional[1]:-}.json"
    [ "${positional[0]:-}" = view ] && [ -f "$f" ] || unexpected "$all"
    reply "$f" ;;
  *) unexpected "$all" ;;
esac
STUB
chmod +x "$bin/gh"

# ---- fixtures --------------------------------------------------------------------
# One open milestone, v0.8.0 (#8), holding whatever a case adds to it.
REPO=galax-io/test
BACKLOG=galax-io/backlog
new_case() { # name
  fx="$tmp/fx-$1"; mkdir -p "$fx"; : > "$fx/items.ndjson"
}
issue_in() { # owner/repo, number, milestone title, [milestone description] — an issue the script may look up
  jq -nc --arg ms "$3" --arg desc "${4:-}" '{milestone: (if $ms == "" then null else {title: $ms, description: $desc} end)}' \
    > "$fx/issue-${1//\//_}-$2.json"
}
add_issue() { # number, state (open|closed) — an issue of this repository in v0.8.0
  printf '{"number":%s,"state":"%s","milestone":{"number":8}}\n' "$1" "$2" >> "$fx/items.ndjson"
  issue_in "$REPO" "$1" v0.8.0
}
AUTHOR=jigarkhwar; AUTHOR_BOT=false; HEAD=feat/something   # a case may change these before pr_json
pr_json() { # number, state (OPEN|CLOSED|MERGED), closing link owner/repo#N or "", body, milestone title or "", [base branch]
  jq -nc --argjson n "$1" --arg state "$2" --arg link "$3" --arg body "$4" --arg ms "$5" --arg base "${6:-main}" \
    --arg author "$AUTHOR" --argjson isbot "$AUTHOR_BOT" --arg head "$HEAD" '{
    number: $n, title: "PR \($n)", state: $state, body: $body, baseRefName: $base,
    author: {login: $author, is_bot: $isbot}, headRefName: $head,
    milestone: (if $ms == "" then null else {title: $ms} end),
    closingIssuesReferences: [$link | select(. != "")
      | capture("^(?<owner>[^/]+)/(?<name>[^#]+)#(?<n>[0-9]+)$")
      | {number: (.n | tonumber), repository: {name, owner: {login: .owner}}}]
  }' > "$fx/pr-$1.json"
}
add_pr() { # number, state, closing link or "", [body] — a PR in v0.8.0
  local rest=closed
  [ "$2" = OPEN ] && rest=open
  printf '{"number":%s,"state":"%s","pull_request":{},"milestone":{"number":8}}\n' "$1" "$rest" >> "$fx/items.ndjson"
  pr_json "$1" "$2" "$3" "${4:-}" v0.8.0
}
# The cases above the strict section keep the local behaviour: they run with the feature
# rule off (FEATURE_LINK=optional). A strict case sets FL=required (the default of the script).
FL=optional
run() { # the script's arguments; sets $out and $rc
  out="$(GH_TOKEN=job-token PATH="$bin:$PATH" REPO="${RUN_REPO:-$REPO}" BACKLOG_REPO="$BACKLOG" FEATURE_LINK="$FL" FIXTURES="$fx" bash "$script" "$@" 2>&1)"; rc=$?
}
has() { grep -qF -- "$1" <<<"$out"; }
only_known_calls() { [ ! -s "$fx/unexpected.log" ]; }

echo "check-linkage.sh (feature rule)"
BUSINESS='Release: business'

# 1. local behaviour kept: a PR closing an issue of its own milestone passes
new_case gate-same
pr_json 3 OPEN "$REPO#1" "Closes #1" v0.8.0
add_issue 1 open
run --pr 3
check "gate: closing an issue of the same milestone passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "gate: it says same milestone" $(has '✓ closes #1 (same milestone)' && echo 0 || echo 1) "out=$out"

# 2. local behaviour kept: closing an issue of another milestone fails
new_case gate-other
pr_json 3 OPEN "$REPO#1" "Closes #1" v0.8.0
issue_in "$REPO" 1 v0.7.0
run --pr 3
check "gate: closing an issue of another milestone fails" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "gate: it names both milestones" $(has '✗ closes #1 but its milestone is "v0.7.0", not "v0.8.0"' && echo 0 || echo 1) "out=$out"

# 3. local behaviour kept: a milestone-only PR passes
new_case gate-milestone-only
pr_json 3 OPEN "" "Just a change." v0.8.0
run --pr 3
check "gate: a milestone-only PR passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 4. gate: a PR with a version milestone may link a feature of the backlog
new_case gate-feature
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "gate: Part of a backlog feature in a business milestone passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "gate: it names the business milestone" $(has "✓ links $BACKLOG#30 (feature of business milestone \"M1 — first run\")" && echo 0 || echo 1) "out=$out"
check "gate: the script made only the calls the stub knows" $(only_known_calls && echo 0 || echo 1) "$(cat "$fx/unexpected.log" 2>&1)"

# 5. gate: the marker may sit among other lines of the milestone description
new_case gate-feature-multiline
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' $'First real run of the process.\nrelease: Business\nDue: autumn'
run --pr 3
check "gate: the marker on its own line of a longer description counts (any case)" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 6. gate: a marker that is not on a line of its own is no marker
new_case gate-feature-inline
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' 'Not a Release: business milestone'
run --pr 3
check "gate: the marker inside a sentence does not make a feature" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 7. gate: a backlog issue in an ordinary milestone is not a feature
new_case gate-backlog-plain
pr_json 3 OPEN "" "Part of $BACKLOG#31" v0.8.0
issue_in "$BACKLOG" 31 'v0.7.0' 'Tooling of the backlog itself.'
run --pr 3
check "gate: a backlog issue in an ordinary milestone still has to match" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "gate: it names both milestones" $(has "✗ links $BACKLOG#31 but its milestone is \"v0.7.0\", not \"v0.8.0\"" && echo 0 || echo 1) "out=$out"

# 8. gate: the marker only counts in the backlog repository
new_case gate-marker-elsewhere
pr_json 3 OPEN "$REPO#1" "Closes #1" v0.8.0
issue_in "$REPO" 1 'M1 — first run' "$BUSINESS"
run --pr 3
check "gate: the marker in another repository does not make a feature" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 9. gate: a backlog issue without a milestone is not a feature
new_case gate-feature-no-milestone
pr_json 3 OPEN "" "Part of $BACKLOG#32" v0.8.0
issue_in "$BACKLOG" 32 ""
run --pr 3
check "gate: a backlog issue without a milestone fails" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 10. gate: the PR itself still needs its version milestone
new_case gate-feature-no-pr-milestone
pr_json 3 OPEN "" "Part of $BACKLOG#30" ""
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "gate: a feature link does not replace the PR's own milestone" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "gate: it says to assign one" $(has '✗ no milestone' && echo 0 || echo 1) "out=$out"
check "gate: the feature link is still reported" $(has "✓ links $BACKLOG#30 (feature of business milestone" && echo 0 || echo 1) "out=$out"

# 11. audit: a PR of the milestone that links a backlog feature is not an error
new_case audit-feature
add_issue 1 open
add_pr 2 OPEN "" "Part of $BACKLOG#30"
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run
check "audit: linking a backlog feature passes the audit" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "audit: the PR is listed with its link" $(has "✓ PR #2 (OPEN) → part of $BACKLOG#30" && echo 0 || echo 1) "out=$out"

# 12. audit: a link to an ordinary backlog issue of another milestone still fails
new_case audit-backlog-plain
add_pr 2 OPEN "" "Part of $BACKLOG#31"
issue_in "$BACKLOG" 31 'v0.7.0' 'Tooling of the backlog itself.'
run
check "audit: a backlog issue of another ordinary milestone fails the audit" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 13. tag: a merged PR of a feature does not block the tag of its repository
new_case tag-feature
add_issue 1 closed
add_pr 2 MERGED "$REPO#1" "Closes #1"
add_pr 3 MERGED "" "Part of $BACKLOG#30"
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --tag
check "tag: a merged PR that links a feature does not block the tag" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "tag: the milestone is tag-ready" $(has 'Milestone #8 is tag-ready.' && echo 0 || echo 1) "out=$out"

# ---- the strict rule: every PR links a feature of the backlog ---------------------
echo "check-linkage.sh (every PR links a feature)"
FL=required
NOFEAT='✗ links no feature of the backlog — add "Part of '"$BACKLOG"'#<feature>" (maintenance with no feature of its own: the standing feature of the active business milestone, the issue with the label standing)'

# 14. gate: a PR that links nothing fails
new_case strict-none
pr_json 3 OPEN "" "Just a change." v0.8.0
run --pr 3
check "strict: a milestone-only PR links no feature and fails" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it says what to add" $(has "$NOFEAT" && echo 0 || echo 1) "out=$out"

# 15. gate: Part of an ordinary backlog issue is no feature
new_case strict-part-plain
pr_json 3 OPEN "" "Part of $BACKLOG#31" v0.8.0
issue_in "$BACKLOG" 31 v0.8.0 'Tooling of the backlog itself.'
run --pr 3
check "strict: Part of a backlog issue that is no feature fails" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it says no feature" $(has '✗ links no feature of the backlog' && echo 0 || echo 1) "out=$out"

# 16. gate: a closing link to an issue of its own repository, without a feature
new_case strict-close-only
pr_json 3 OPEN "$REPO#1" "Closes #1" v0.8.0
add_issue 1 open
run --pr 3
check "strict: closing an own issue without a feature fails" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it says no feature" $(has '✗ links no feature of the backlog' && echo 0 || echo 1) "out=$out"

# 17. gate: Part of a feature passes
new_case strict-feature
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "strict: Part of a feature passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it names the feature once" $([ "$(grep -cF "links $BACKLOG#30" <<<"$out")" -eq 1 ] && echo 0 || echo 1) "out=$out"

# 18. gate: a closing link to an own issue, with a feature next to it
new_case strict-close-and-feature
pr_json 3 OPEN "$REPO#1" "Closes #1
Part of $BACKLOG#30" v0.8.0
add_issue 1 open
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "strict: a closing link plus Part of a feature passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: the feature next to the closing link is seen" $(has "✓ links $BACKLOG#30 (feature of business milestone" && echo 0 || echo 1) "out=$out"

# 19. gate: a closing link to the feature itself
new_case strict-close-feature
pr_json 3 OPEN "$BACKLOG#30" "Closes $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "strict: a closing link to a feature passes" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"

# 20-22. gate: a bot's PR is exempt
new_case strict-bot-author
AUTHOR="dependabot[bot]"; AUTHOR_BOT=true
pr_json 3 OPEN "" "Bumps a pin." v0.8.0
run --pr 3
check "strict: a PR by a bot passes without a feature" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it says a bot needs none" $(has "a bot's PR needs no feature" && echo 0 || echo 1) "out=$out"
AUTHOR=jigarkhwar; AUTHOR_BOT=false

new_case strict-dependabot-branch
HEAD=dependabot/sbt/foo-1.2
pr_json 3 OPEN "" "Bumps a pin." v0.8.0
run --pr 3
check "strict: a dependabot/ branch passes without a feature" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"

new_case strict-update-branch
HEAD=update/foo-1.2
pr_json 3 OPEN "" "Updates foo." v0.8.0
run --pr 3
check "strict: an update/ branch passes without a feature" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
HEAD=feat/something

# 23. gate: a bot's PR still needs its milestone
new_case strict-bot-no-milestone
HEAD=dependabot/sbt/foo-1.2
pr_json 3 OPEN "" "Bumps a pin." ""
run --pr 3
check "strict: a bot's PR still needs a milestone" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
HEAD=feat/something

# 24. gate: the backlog repository itself is exempt
new_case strict-backlog-repo
pr_json 3 OPEN "" "Just a change." v0.8.0
RUN_REPO=$REPO run --pr 3
check "strict: a PR of a repository other than the backlog fails (control)" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
BACKLOG_SAVE=$BACKLOG; BACKLOG=$REPO
run --pr 3
check "strict: a PR of the backlog repository needs no feature" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
BACKLOG=$BACKLOG_SAVE

# 25. env: FEATURE_LINK=optional lets a PR without a feature pass; anything else is refused
new_case strict-optional
pr_json 3 OPEN "" "Just a change." v0.8.0
FL=optional run --pr 3
check "strict: FEATURE_LINK=optional lets a PR without a feature pass" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
FL=sometimes run --pr 3
check "strict: any other FEATURE_LINK is refused with exit 2" $([ "$rc" -eq 2 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict: it says what is allowed" $(has 'FEATURE_LINK must be required or optional' && echo 0 || echo 1) "out=$out"

# 26. audit: a PR without a feature is a warning, not an error
new_case strict-audit
add_pr 2 OPEN "" "Just a change."
run
check "strict audit: a PR without a feature only warns" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "strict audit: the warning names the PR" $(has "! PR #2 links no feature of the backlog" && echo 0 || echo 1) "out=$out"

new_case strict-audit-feature
add_pr 2 OPEN "" "Part of $BACKLOG#30"
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run
check "strict audit: a PR with a feature draws no warning" $(! has 'links no feature' && echo 0 || echo 1) "out=$out"

new_case strict-audit-bot
AUTHOR="app/dependabot"; AUTHOR_BOT=true
add_pr 2 OPEN "" "Bumps a pin."
run
check "strict audit: a bot's PR draws no warning" $(! has 'links no feature' && echo 0 || echo 1) "out=$out"
AUTHOR=jigarkhwar; AUTHOR_BOT=false

# 27-29. the private backlog is read with BACKLOG_READ_TOKEN, and only for calls about the backlog
new_case token-hidden
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
: > "$fx/backlog-hidden"
run --pr 3
check "token: an unreadable backlog fails the gate" $([ "$rc" -eq 1 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "token: it names BACKLOG_READ_TOKEN" $(has 'give the job BACKLOG_READ_TOKEN' && echo 0 || echo 1) "out=$out"
check "token: it does not claim the PR links no feature" $(! has 'links no feature' && echo 0 || echo 1) "out=$out"

new_case token-given
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
: > "$fx/backlog-hidden"
BACKLOG_READ_TOKEN=backlog-secret run --pr 3
check "token: with BACKLOG_READ_TOKEN the feature is seen" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "token: backlog calls carry the backlog token" $(grep -F "$BACKLOG" "$fx/calls.log" | grep -qv 'GH_TOKEN=backlog-secret' && echo 1 || echo 0) "$(cat "$fx/calls.log")"
check "token: the PR is read with the job token" $(grep -F 'pr view' "$fx/calls.log" | grep -q 'GH_TOKEN=job-token' && echo 0 || echo 1) "$(cat "$fx/calls.log")"
check "token: the backlog token is used for nothing else" $(grep -F 'GH_TOKEN=backlog-secret' "$fx/calls.log" | grep -qv "$BACKLOG" && echo 1 || echo 0) "$(cat "$fx/calls.log")"

new_case token-readable
pr_json 3 OPEN "" "Part of $BACKLOG#30" v0.8.0
issue_in "$BACKLOG" 30 'M1 — first run' "$BUSINESS"
run --pr 3
check "token: a readable backlog needs no token" $([ "$rc" -eq 0 ] && echo 0 || echo 1) "rc=$rc out=$out"
check "token: and says nothing about one" $(! has 'BACKLOG_READ_TOKEN' && echo 0 || echo 1) "out=$out"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
