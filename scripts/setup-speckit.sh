#!/usr/bin/env bash
# Install spec-kit community extensions and presets.
#
# Native extensions (agent-context, bug, git) install by name from the catalog.
# Community ones live in a discovery-only catalog (install_allowed: false), so
# they must be installed via --from <github-archive-url>.
#
# Usage:
#   bash setup-speckit.sh
#   bash setup-speckit.sh --force

set -euo pipefail

FORCE_FLAG=""
[[ "${1:-}" == "--force" ]] && FORCE_FLAG="--force"

run_ext() {     # id  <add-args...>
  local id="$1"; shift
  local out
  if out=$(specify extension add "$@" ${FORCE_FLAG} 2>&1); then
    echo "  ✓  $id"
  elif echo "$out" | grep -q "already installed"; then
    echo "  –  $id (already installed, use --force to reinstall)"
  else
    echo "  ✗  $id"; echo "$out"; return 1
  fi
}

run_preset() {  # id  <add-args...>
  local id="$1"; shift
  local out
  if out=$(specify preset add "$@" ${FORCE_FLAG} 2>&1); then
    echo "  ✓  $id"
  elif echo "$out" | grep -q "already installed"; then
    echo "  –  $id (already installed, use --force to reinstall)"
  else
    echo "  ✗  $id"; echo "$out"; return 1
  fi
}

gh_zip() { echo "https://github.com/$1/archive/refs/tags/$2.zip"; }

echo
echo "==> Native extensions (catalog by name)"
run_ext agent-context  agent-context
run_ext bug            bug
run_ext git            git

echo
echo "==> Community extensions (--from github archive)"
run_ext worktrees  worktrees  --from "$(gh_zip dango85/spec-kit-worktree-parallel  v1.3.2)"
run_ext changelog  changelog  --from "$(gh_zip Quratulain-bilal/spec-kit-changelog v1.0.0)"
run_ext harness    harness    --from "$(gh_zip formin/spec-kit-harness            v1.0.0)"
run_ext spectest   spectest   --from "$(gh_zip Quratulain-bilal/spec-kit-spectest v1.0.0)"

echo
echo "==> Community presets (--from github archive)"
run_preset claude-ask-questions  claude-ask-questions \
  --from "$(gh_zip 0xrafasec/spec-kit-preset-claude-ask-questions v1.0.0)"

echo
echo "Done."
