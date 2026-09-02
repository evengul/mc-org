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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

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
# from the main checkout (every line except the DB_ and PORT ones), then append
# the branch's Neon credentials. The script fully owns the worktree's local.env.
#
# PORT is stripped for the same reason as DB_*: it is per-worktree, so inheriting
# the main checkout's would hand every worktree the same one. But this script is
# re-runnable, so carry over the port this worktree was already allocated before
# the rewrite — see worktree-port.sh on why the port must not move (MCO-476).
PREV_PORT=""
if [ -f "$ENV_FILE" ]; then
  PREV_PORT="$(grep -E '^PORT=[0-9]+$' "$ENV_FILE" | tail -n1 | cut -d= -f2 || true)"
fi
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
  grep -vE '^(DB_URL|DB_USER|DB_PASSWORD|PORT)=' "$MAIN_ENV" || true
  printf 'DB_URL=%s\nDB_USER=%s\nDB_PASSWORD=%s\n' "$JDBC_URL" "$DB_ROLE" "$DB_PASSWORD"
  # Not `[ -n "$PREV_PORT" ] && printf ...`: an empty PREV_PORT makes that the group's last
  # command with status 1, and `set -e` would kill the script on the common fresh-worktree path.
  if [ -n "$PREV_PORT" ]; then printf 'PORT=%s\n' "$PREV_PORT"; fi
} > "$ENV_FILE"

# Allocates and appends PORT only when the block above did not carry one over.
WORKTREE_PORT="$(bash "$SCRIPT_DIR/worktree-port.sh" "$WORKTREE_ROOT")"

echo "worktree-db: wrote local.env (inherited from main checkout) pointing at ${DB_HOST}, port ${WORKTREE_PORT}"

# --- Migrate ----------------------------------------------------------------
echo "worktree-db: running Flyway migrations against the worktree branch..."
(
  cd "$WORKTREE_ROOT/webapp"
  DB_URL="$JDBC_URL" DB_USER="$DB_ROLE" DB_PASSWORD="$DB_PASSWORD" \
    mvn -q flyway:migrate -pl mc-web
)


# --- Seed the demo sign-in user, with access to every world -----------------
# Without this, the first local sign-in in a fresh worktree lands you on "You
# don't have permission to access this world" for every world in the fork.
#
# Why it happens: the Neon branch inherits production's worlds and their
# world_members rows, but not *your* local user — DemoSignInPipeline mints one
# on first sign-in, and a brand-new user is a member of nothing. Worse, the
# role check is cached per process, so granting access afterwards needs a
# server restart to take effect.
#
# Why it can be done up front: the demo profile is deterministic.
# DemoSignInPipeline derives its uuid as "${DEMO_USER}-uuid", and
# CreateUserIfNotExistsStep looks the user up by minecraft_profiles.uuid — so
# seeding that row now means the first sign-in *finds* this user rather than
# creating another, with membership already in place and nothing stale cached.
#
# Both statements are idempotent (minecraft_profiles.uuid is UNIQUE), so a
# re-run is a no-op, and a world added later is picked up by re-running.
DEMO_USER="$(grep -E '^DEMO_USER=' "$ENV_FILE" | tail -n1 | cut -d= -f2- || true)"
if [ -z "$DEMO_USER" ]; then
  echo "worktree-db: DEMO_USER unset in local.env — skipping demo-user seeding."
elif ! command -v psql >/dev/null 2>&1; then
  echo "worktree-db: psql not found — skipping demo-user seeding (sign-in will have no world access)."
else
  PSQL_URL="postgresql://${DB_ROLE}:${DB_PASSWORD}@${DB_HOST}/${DB_NAME}?sslmode=require"
  psql "$PSQL_URL" -q -v ON_ERROR_STOP=1 \
    -v uuid="${DEMO_USER}-uuid" -v name="$DEMO_USER" <<'SQL'
WITH new_user AS (
    INSERT INTO users (created_at, updated_at)
    SELECT now(), now()
    WHERE NOT EXISTS (SELECT 1 FROM minecraft_profiles WHERE uuid = :'uuid')
    RETURNING id
)
INSERT INTO minecraft_profiles (user_id, uuid, username)
SELECT id, :'uuid', :'name' FROM new_user;

-- world_role 0 is Role.OWNER (mc-domain/user/Role.kt) — a worktree DB is a
-- disposable fork, so the useful default is "can touch everything".
INSERT INTO world_members (user_id, world_id, display_name, world_role, pinned)
SELECT p.user_id, w.id, :'name', 0, false
FROM minecraft_profiles p
CROSS JOIN world w
WHERE p.uuid = :'uuid'
  AND NOT EXISTS (
      SELECT 1 FROM world_members m WHERE m.world_id = w.id AND m.user_id = p.user_id
  );
SQL
  echo "worktree-db: demo user '${DEMO_USER}' seeded with owner access to every world."
fi

echo "worktree-db: ready. Neon branch '${NEON_BRANCH}' is isolated to this worktree."
