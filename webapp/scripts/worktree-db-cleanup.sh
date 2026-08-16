#!/usr/bin/env bash
# Delete Neon worktree branches (wt/*).
#
#   bash webapp/scripts/worktree-db-cleanup.sh            # delete the branch for the current worktree
#   bash webapp/scripts/worktree-db-cleanup.sh wt/foo     # delete a named branch
#   bash webapp/scripts/worktree-db-cleanup.sh --prune    # delete every wt/* with no matching git worktree
#
# Runs automatically (--prune) via the ExitWorktree PostToolUse hook, which
# reconciles live Neon wt/* branches against `git worktree list` and removes
# the orphans.

set -euo pipefail

NEON_PROJECT_ID="sweet-dust-00910797"

# Run git commands from the directory the script physically lives in (the main
# checkout), so prune works even when the just-removed worktree was the cwd.
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

list_wt_branches() {
  neonctl branches list --project-id "$NEON_PROJECT_ID" --output json \
    | python3 -c 'import sys,json
d=json.load(sys.stdin)
for b in (d if isinstance(d,list) else d.get("branches",[])):
    if b.get("name","").startswith("wt/"): print(b["name"])'
}

delete_branch() {
  echo "worktree-db-cleanup: deleting Neon branch $1..."
  neonctl branches delete "$1" --project-id "$NEON_PROJECT_ID" --force >/dev/null
}

prune() {
  local active nb wt orphans=0
  # --porcelain, not the human format. `git worktree list` appends annotations *after* the
  # bracketed branch ("[branch] locked", "[branch] prunable"), which a bracket-stripping sed
  # leaves on the line — so an exact-line match against the branch name fails and the worktree
  # reads as an orphan. That deleted a live locked worktree's database once; the porcelain form
  # emits one "branch refs/heads/<name>" line with nothing appended.
  active="$(git worktree list --porcelain | sed -n 's|^branch refs/heads/||p')"
  while IFS= read -r nb; do
    [ -z "$nb" ] && continue
    wt="${nb#wt/}"
    if ! grep -qxF "$wt" <<<"$active"; then
      delete_branch "$nb"; orphans=$((orphans + 1))
    fi
  done < <(list_wt_branches)
  echo "worktree-db-cleanup: pruned ${orphans} orphaned branch(es)."
}

case "${1:-}" in
  --prune) prune ;;
  "")
    branch="wt/$(git branch --show-current)"
    delete_branch "$branch"
    ;;
  *) delete_branch "$1" ;;
esac
