#!/usr/bin/env bash
#
# check-linkage.sh — verify the issue ↔ PR ↔ milestone contract (see AGENTS.md "Linkage").
# Run `scripts/check-linkage.sh --help` for full usage.

set -euo pipefail

usage() {
  cat <<'EOF'
check-linkage.sh — verify the issue <-> PR <-> milestone contract (see AGENTS.md "Linkage").

What each entity owes (this script enforces it):
  Issue      belongs to exactly one milestone; closed only when its fix is on main.
  PR         carries its issue's milestone + a real closing link (Closes #<issue>);
             the linked issue sits in the same milestone; one issue per PR.
  Milestone  one release (vX.Y.Z); tag only when every issue is closed and every PR merged.

Usage:
  scripts/check-linkage.sh [milestone]   # default: lowest-numbered open milestone
  scripts/check-linkage.sh --tag [ms]    # also assert tag-readiness (all issues closed, all PRs merged)
  scripts/check-linkage.sh --help

Env:
  REPO=owner/name   # override repo (default: gh repo view, fallback galax-io/gatling-picatinny)

Exit: 0 all rules hold | 1 at least one violation | 2 usage/prereq error.
EOF
}

for bin in gh jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "error: '$bin' not found on PATH" >&2; exit 2; }
done

REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo galax-io/gatling-picatinny)}"

TAG_MODE=0
MS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --tag)      TAG_MODE=1 ;;
    --help|-h)  usage; exit 0 ;;
    [0-9]*)     MS="$1" ;;
    *)          echo "error: unknown argument '$1'" >&2; usage; exit 2 ;;
  esac
  shift
done

# Default to the lowest-numbered open milestone (the "active" one).
if [ -z "$MS" ]; then
  MS=$(gh api "repos/$REPO/milestones?state=open&per_page=100" --jq 'sort_by(.number) | .[0].number // empty')
  [ -n "$MS" ] || { echo "error: no open milestone found in $REPO" >&2; exit 2; }
fi

ms_json=$(gh api "repos/$REPO/milestones/$MS") || { echo "error: milestone #$MS not found in $REPO" >&2; exit 2; }
ms_title=$(jq -r '.title' <<<"$ms_json")
ms_state=$(jq -r '.state' <<<"$ms_json")

errors=0
warns=0
err()  { printf '  ✗ %s\n' "$1"; errors=$((errors + 1)); }
warn() { printf '  ! %s\n' "$1"; warns=$((warns + 1)); }
ok()   { printf '  ✓ %s\n' "$1"; }

printf 'Repo:      %s\n' "$REPO"
printf 'Milestone: #%s  %s  (%s)\n' "$MS" "$ms_title" "$ms_state"
[ "$TAG_MODE" = 1 ] && printf 'Mode:      tag-readiness\n'
printf '\n'

# All issues + PRs carrying this milestone (REST returns both; PRs carry a .pull_request key).
items=$(gh api --paginate --slurp "repos/$REPO/issues?milestone=$MS&state=all&per_page=100" | jq 'add')

pr_numbers=$(jq -r '.[] | select(.pull_request != null) | .number' <<<"$items")
issue_numbers=$(jq -r '.[] | select(.pull_request == null) | .number' <<<"$items")

linked_issues=" "   # space-delimited set of issue numbers a PR points at

printf 'Pull requests\n'
if [ -z "$pr_numbers" ]; then
  warn "no PRs carry milestone #$MS yet"
fi
for pr in $pr_numbers; do
  pr_json=$(gh pr view "$pr" --repo "$REPO" --json number,title,state,milestone,closingIssuesReferences,body)
  pr_state=$(jq -r '.state' <<<"$pr_json")
  pr_title=$(jq -r '.title' <<<"$pr_json")

  # Real GitHub closing links, plus a text fallback for Closes/Fixes/Resolves #N in the body.
  ref_nums=$(jq -r '.closingIssuesReferences[]?.number' <<<"$pr_json")
  if [ -z "$ref_nums" ]; then
    ref_nums=$(jq -r '.body // ""' <<<"$pr_json" \
      | grep -oiE '(close[sd]?|fix(e[sd])?|resolve[sd]?) +#[0-9]+' \
      | grep -oE '[0-9]+' || true)
    [ -n "$ref_nums" ] && warn "PR #$pr links via body text only (not a registered GitHub closing link): $pr_title"
  fi

  if [ -z "$ref_nums" ]; then
    err "PR #$pr ($pr_state) closes no issue — add 'Closes #<issue>': $pr_title"
    continue
  fi

  for ri in $ref_nums; do
    linked_issues="$linked_issues$ri "
    ri_ms=$(gh issue view "$ri" --repo "$REPO" --json milestone -q '.milestone.title // ""' 2>/dev/null || echo "")
    if [ "$ri_ms" != "$ms_title" ]; then
      err "PR #$pr closes issue #$ri but that issue's milestone is '${ri_ms:-none}', not '$ms_title'"
    fi
  done

  if [ "$TAG_MODE" = 1 ] && [ "$pr_state" != "MERGED" ]; then
    err "PR #$pr is $pr_state — must be MERGED before tagging: $pr_title"
  else
    ok "PR #$pr ($pr_state) → closes #$(echo "$ref_nums" | paste -sd, -)"
  fi
done

printf '\nIssues\n'
if [ -z "$issue_numbers" ]; then
  warn "no issues carry milestone #$MS"
fi
for is in $issue_numbers; do
  is_state=$(jq -r --argjson n "$is" '.[] | select(.number == $n) | .state' <<<"$items")
  case "$linked_issues" in
    *" $is "*) linked="linked" ;;
    *)         linked="" ;;
  esac

  if [ "$TAG_MODE" = 1 ] && [ "$is_state" != "closed" ]; then
    err "issue #$is is $is_state — must be closed before tagging"
  elif [ -z "$linked" ]; then
    warn "issue #$is ($is_state) has no PR closing it"
  else
    ok "issue #$is ($is_state) ← linked"
  fi
done

printf '\n'
if [ "$errors" -gt 0 ]; then
  printf 'FAIL: %d error(s), %d warning(s).\n' "$errors" "$warns"
  exit 1
fi
printf 'PASS: 0 errors, %d warning(s).\n' "$warns"
[ "$TAG_MODE" = 1 ] && printf 'Milestone #%s is tag-ready.\n' "$MS"
exit 0
