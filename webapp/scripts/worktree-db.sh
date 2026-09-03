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

# --- Install the module jars into this worktree's Maven repository ----------
# The worktree's Maven repository (worktree-m2.sh) symlinks every third-party
# groupId back to ~/.m2 but keeps app/mcorg as a real, EMPTY directory. Until
# something installs into it, every `-pl <module>` command in this worktree fails
# to resolve its siblings — the MCO-285 rule, with an empty repository rather than
# a stale one. That included the flyway step below, which used to abort this whole
# script and take the demo-user seeding with it (MCO-510).
#
# No `clean`: a fresh worktree has nothing to clean, and Kotlin incremental
# compilation is off since MCO-378, so an ordinary install is already a full
# rebuild (~13s for six modules).
#
# `-DskipTests` rather than `-Dmaven.test.skip=true`: mc-web depends on
# app.mcorg:mc-pipeline:jar:tests, so the test jar still has to be built — just
# not run.
echo "worktree-db: installing module jars into the worktree's Maven repository..."
BUILD_OK=1
(
  cd "$WORKTREE_ROOT/webapp"
  mvn -q -DskipTests install
) || BUILD_OK=0
if [ "$BUILD_OK" = 0 ]; then
  echo "worktree-db: build failed — skipping migrations, but continuing to the seeding below." >&2
fi

# --- Migrate ----------------------------------------------------------------
# Deliberately non-fatal, and deliberately ABOVE the seeding rather than fatal
# before it. The fork is a copy of an already-migrated production branch, so this
# step is normally a no-op; the seeding is the part a fresh worktree actually
# cannot do without. Letting a build or migration problem abort the script is what
# left two worktrees silently unseeded (MCO-510) — the hook discards output, so it
# looked like provisioning had worked. Failures are reported at the end instead.
MIGRATE_OK=1
if [ "$BUILD_OK" = 1 ]; then
  echo "worktree-db: running Flyway migrations against the worktree branch..."
  (
    cd "$WORKTREE_ROOT/webapp"
    DB_URL="$JDBC_URL" DB_USER="$DB_ROLE" DB_PASSWORD="$DB_PASSWORD" \
      mvn -q flyway:migrate -pl mc-web
  ) || MIGRATE_OK=0
  if [ "$MIGRATE_OK" = 0 ]; then
    echo "worktree-db: flyway:migrate failed — continuing to the seeding below." >&2
  fi
else
  MIGRATE_OK=0
fi


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
SEED_OK=1
if [ -z "$DEMO_USER" ]; then
  # Not a failure: DEMO_USER is optional (documentation/configuration.md), and unset
  # means demo sign-in is deliberately off. Nothing to seed for.
  echo "worktree-db: DEMO_USER unset in local.env — skipping demo-user seeding."
elif ! command -v psql >/dev/null 2>&1; then
  SEED_OK=0
  echo "worktree-db: psql not found — skipping demo-user seeding (sign-in will have no world access)."
else
  PSQL_URL="postgresql://${DB_ROLE}:${DB_PASSWORD}@${DB_HOST}/${DB_NAME}?sslmode=require"
  psql "$PSQL_URL" -q -v ON_ERROR_STOP=1 \
    -v uuid="${DEMO_USER}-uuid" -v name="$DEMO_USER" <<'SQL' || SEED_OK=0
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

-- Hand the forked idea bank to the demo user as well, or it is invisible here.
--
-- GetIdeaProducersStep gates on `i.visibility = 'PUBLIC' OR i.created_by = <viewer>`,
-- and production's bank is overwhelmingly PRIVATE and authored by the real account
-- (11 of 12 entries at the time of writing). The fork inherits those rows verbatim, so
-- a worktree's demo user — a different id — sees only the public ones. The visible
-- symptom is not an error: "Worth a farm" simply renders its demand lines with no
-- designs under them, which reads exactly like an idea bank that has nothing to offer.
-- That cost two separate agents an afternoon of debugging a feature that was working.
--
-- Repointing authorship is safe precisely here: a wt/* branch is a disposable fork that
-- is never merged back, and its whole purpose is to be driven by this one user. Only
-- PRIVATE rows are touched — public ones are already visible, so moving them would
-- change authorship for no gain. Idempotent: the second run matches nothing.
UPDATE ideas
SET created_by = p.user_id
FROM minecraft_profiles p
WHERE p.uuid = :'uuid'
  AND ideas.visibility = 'PRIVATE'
  AND ideas.created_by <> p.user_id;
SQL
  # Check the rows are actually there rather than trusting psql's exit code. This is
  # the assertion that would have caught MCO-510 on the day it started: the seeding
  # never ran at all, and every other signal (local.env, PORT, an exit-0 hook) said
  # provisioning had succeeded.
  HAS_PROFILE="$(psql "$PSQL_URL" -tAc \
    "SELECT count(*) FROM minecraft_profiles WHERE uuid = '${DEMO_USER}-uuid'" 2>/dev/null || echo 0)"
  SEEDED_WORLDS="$(psql "$PSQL_URL" -tAc \
    "SELECT count(*) FROM world_members m
       JOIN minecraft_profiles p ON p.user_id = m.user_id
      WHERE p.uuid = '${DEMO_USER}-uuid'" 2>/dev/null || echo 0)"
  # A fork with no worlds at all is fine — the profile is what must exist; the
  # membership count is only required to keep up with however many worlds there are.
  TOTAL_WORLDS="$(psql "$PSQL_URL" -tAc "SELECT count(*) FROM world" 2>/dev/null || echo 0)"
  if [ "$SEED_OK" = 1 ] && [ "$HAS_PROFILE" = "1" ] && [ "$SEEDED_WORLDS" = "$TOTAL_WORLDS" ]; then
    echo "worktree-db: demo user '${DEMO_USER}' seeded with owner access to all ${TOTAL_WORLDS} worlds and the idea bank."
  else
    SEED_OK=0
    echo "worktree-db: demo-user seeding did NOT take (profile=${HAS_PROFILE}, ${SEEDED_WORLDS}/${TOTAL_WORLDS} worlds)." >&2
    echo "worktree-db: sign-in here will report 'You don't have permission' — re-run this script." >&2
  fi
fi

# --- Report -----------------------------------------------------------------
# Exit non-zero if anything failed, but only after the seeding has had its turn.
# The order is the whole point: the steps that can fail run around the one that
# must not be skipped, and the bad news arrives at the end rather than as an early
# exit (MCO-510).
if [ "$BUILD_OK" = 1 ] && [ "$MIGRATE_OK" = 1 ] && [ "$SEED_OK" = 1 ]; then
  echo "worktree-db: ready. Neon branch '${NEON_BRANCH}' is isolated to this worktree."
else
  echo "worktree-db: FINISHED WITH PROBLEMS — build=$BUILD_OK migrate=$MIGRATE_OK seed=$SEED_OK" >&2
  echo "worktree-db: branch '${NEON_BRANCH}' exists and local.env points at it; re-run this script after fixing the build." >&2
  exit 1
fi
