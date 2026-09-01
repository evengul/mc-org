#!/usr/bin/env bash
# Give this git worktree its own HTTP port, so several dev servers can run at once (MCO-476).
#
# The port is written into the worktree's webapp/local.env as PORT=<n> and echoed on stdout.
# It is allocated once and then STABLE: re-running this script returns the port already in
# local.env rather than picking a new one. A port that moved between runs would break bookmarks
# and make APP_HOST unpinnable.
#
# The main checkout deliberately gets nothing — no PORT means 8080, which is what the Dockerfile's
# EXPOSE, both fly.toml `internal_port` values and every doc assume.
#
# Called by worktree-db.sh (which owns local.env) and, as a fallback for worktrees made outside an
# agent run, by run.sh. Safe to run by hand from a worktree root:
#   bash webapp/scripts/worktree-port.sh

set -euo pipefail

PORT_MIN=8081          # 8080 belongs to the main checkout
PORT_MAX=8179

# --- Resolve the worktree root ---------------------------------------------
# Same contract as worktree-db.sh: explicit path arg, then the hook's stdin `cwd`, then $PWD.
TARGET_DIR="${1:-}"
if [ -z "$TARGET_DIR" ] && [ ! -t 0 ]; then
  STDIN_JSON="$(cat || true)"
  if [ -n "$STDIN_JSON" ]; then
    TARGET_DIR="$(printf '%s' "$STDIN_JSON" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("cwd",""))
except Exception: print("")' 2>/dev/null || true)"
  fi
fi
TARGET_DIR="${TARGET_DIR:-$PWD}"

WORKTREE_ROOT="$(git -C "$TARGET_DIR" rev-parse --show-toplevel)"
MAIN_REPO="$(git -C "$TARGET_DIR" rev-parse --path-format=absolute --git-common-dir | sed 's|/\.git$||')"

if [ "$WORKTREE_ROOT" = "$MAIN_REPO" ]; then
  # Not an error — the main checkout keeps the default 8080.
  exit 0
fi

ENV_FILE="$WORKTREE_ROOT/webapp/local.env"

# --- Already allocated? -----------------------------------------------------
if [ -f "$ENV_FILE" ]; then
  EXISTING="$(grep -E '^PORT=[0-9]+$' "$ENV_FILE" | tail -n1 | cut -d= -f2 || true)"
  if [ -n "$EXISTING" ]; then
    printf '%s\n' "$EXISTING"
    exit 0
  fi
fi

# --- Allocate ---------------------------------------------------------------
# Two worktrees provisioning at the same time would otherwise both see the same free port and both
# take it, so serialise on a lock in the main repo.
LOCK_FILE="$MAIN_REPO/.git/worktree-port.lock"
exec 9>"$LOCK_FILE"
flock 9 2>/dev/null || true

# The claim set is NOT just "what is listening" — a worktree whose server is stopped still owns its
# port, and handing it to a sibling would collide the next time both are running.
listening_ports() {
  ss -tlnH 2>/dev/null | awk '{print $4}' | sed 's/.*[:.]//' | grep -E '^[0-9]+$' || true
}

claimed_ports() {
  git -C "$MAIN_REPO" worktree list --porcelain 2>/dev/null \
    | awk '/^worktree /{print substr($0, 10)}' \
    | while read -r wt; do
        [ -f "$wt/webapp/local.env" ] || continue
        grep -E '^PORT=[0-9]+$' "$wt/webapp/local.env" | cut -d= -f2 || true
      done
}

TAKEN="$( { listening_ports; claimed_ports; } | sort -u )"

PORT=""
for candidate in $(seq "$PORT_MIN" "$PORT_MAX"); do
  if ! printf '%s\n' "$TAKEN" | grep -qx "$candidate"; then
    PORT="$candidate"
    break
  fi
done

if [ -z "$PORT" ]; then
  echo "worktree-port: no free port in ${PORT_MIN}-${PORT_MAX}." >&2
  exit 1
fi

if [ -f "$ENV_FILE" ]; then
  printf 'PORT=%s\n' "$PORT" >> "$ENV_FILE"
fi

printf '%s\n' "$PORT"
