#!/usr/bin/env bash
# Provision an isolated Neon database branch for the current git worktree.
#
# Forks a copy-on-write branch (wt/<git-branch>) from the production Neon
# branch (master), points this worktree's webapp/local.env at it, and runs
# Flyway migrations. Each worktree gets its own database, so migrations and
# data never collide with the main checkout or with sibling worktrees.
#
# This mirrors what CI already does per pull request (.github/workflows/dev.yml
# creates dev/pr-<N> branches) — only here it runs locally, per worktree.
#
# Runs automatically via the EnterWorktree PostToolUse hook. For worktrees
# created outside an agent run (e.g. `claude -w` or `git worktree add`), run it
# manually from the worktree root:
#   bash webapp/scripts/worktree-db.sh
#
# Teardown happens via the ExitWorktree hook, or manually:
#   bash webapp/scripts/worktree-db-cleanup.sh

set -euo pipefail

NEON_PROJECT_ID="sweet-dust-00910797"
NEON_PARENT="master"        # the production / default Neon branch
DB_NAME="mcorg"
DB_ROLE="mcorg_owner"

# --- Resolve the worktree root ---------------------------------------------
# Prefer an explicit path arg, then the hook's stdin `cwd`, then $PWD.
# Normalise to the git worktree top-level either way.
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
GIT_BRANCH="$(git -C "$WORKTREE_ROOT" branch --show-current)"

if [ -z "$GIT_BRANCH" ]; then
  echo "worktree-db: not on a named branch — skipping." >&2
  exit 0
fi
if [ "$WORKTREE_ROOT" = "$MAIN_REPO" ]; then
  echo "worktree-db: refusing to isolate the main checkout ($MAIN_REPO)." >&2
  echo "worktree-db: run this from a git worktree, not the primary working tree." >&2
  exit 0
fi

NEON_BRANCH="wt/${GIT_BRANCH}"
ENV_FILE="$WORKTREE_ROOT/webapp/local.env"

# --- Create (or reuse) the Neon branch -------------------------------------
echo "worktree-db: creating Neon branch '${NEON_BRANCH}' forked from '${NEON_PARENT}'..."
if ! neonctl branches create \
      --project-id "$NEON_PROJECT_ID" \
      --name "$NEON_BRANCH" \
      --parent "$NEON_PARENT" \
      --output json >/dev/null 2>&1; then
  echo "worktree-db: branch may already exist; reusing it." >&2
fi

CONN="$(neonctl connection-string \
  --project-id "$NEON_PROJECT_ID" \
  --branch "$NEON_BRANCH" \
  --role-name "$DB_ROLE" \
  --database-name "$DB_NAME" \
  --pooled)"

# Parse host + password out of postgresql://user:pass@host/db?params
read -r DB_HOST DB_PASSWORD < <(printf '%s' "$CONN" | python3 -c 'import sys,urllib.parse as u
p=u.urlparse(sys.stdin.read().strip())
print(p.hostname, p.password)')

# Match the JDBC shape used in fly.toml (no channel_binding param).
JDBC_URL="jdbc:postgresql://${DB_HOST}/${DB_NAME}?sslmode=require"

# --- Build this worktree's local.env ---------------------------------------
# local.env is gitignored, so a fresh worktree starts without one. Derive it
# from the main checkout (every line except the DB_ ones), then append the
# branch's Neon credentials. The script fully owns the worktree's local.env.
MAIN_ENV="$MAIN_REPO/webapp/local.env"
if [ ! -f "$MAIN_ENV" ]; then
  # Fresh clone with no local.env yet — fall back to the committed template so
  # worktrees still get the non-DB config (DB_* lines are stripped below anyway).
  MAIN_ENV="$MAIN_REPO/webapp/local.env.example"
fi
if [ ! -f "$MAIN_ENV" ]; then
  echo "worktree-db: no local.env or local.env.example in $MAIN_REPO/webapp." >&2
  exit 1
fi
{
  grep -vE '^(DB_URL|DB_USER|DB_PASSWORD)=' "$MAIN_ENV" || true
  printf 'DB_URL=%s\nDB_USER=%s\nDB_PASSWORD=%s\n' "$JDBC_URL" "$DB_ROLE" "$DB_PASSWORD"
} > "$ENV_FILE"

echo "worktree-db: wrote local.env (inherited from main checkout) pointing at ${DB_HOST}"

# --- Migrate ----------------------------------------------------------------
echo "worktree-db: running Flyway migrations against the worktree branch..."
(
  cd "$WORKTREE_ROOT/webapp"
  DB_URL="$JDBC_URL" DB_USER="$DB_ROLE" DB_PASSWORD="$DB_PASSWORD" \
    mvn -q flyway:migrate -pl mc-web
)

echo "worktree-db: ready. Neon branch '${NEON_BRANCH}' is isolated to this worktree."
