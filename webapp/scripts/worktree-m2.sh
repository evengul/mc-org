#!/usr/bin/env bash
# Give this git worktree its own Maven local repository for the project's OWN
# artifacts, so concurrent worktrees stop overwriting each other's installs.
#
# The problem: run.sh / ingest-locally.sh (and any `-pl <module>` build) `mvn
# install` the six app.mcorg modules into the shared ~/.m2. Every worktree
# installs to the SAME path — the version is a fixed 0.0.1, not a per-branch
# SNAPSHOT — so worktree B's install silently replaces the jars worktree A is
# about to run against. That is MCO-285's NoSuchMethodError, except `-am` does
# not save you: the jar was correct when it was written and wrong a second later.
#
# The fix: a worktree-local repository at webapp/.m2/repository which is a
# SYMLINK FARM over the shared ~/.m2/repository. Every third-party groupId is a
# symlink back to the shared cache (immutable release artifacts — sharing them
# is the whole point, and nothing is re-downloaded or duplicated on disk). Only
# app/mcorg is a real directory, private to this worktree. Installs land there;
# resolution of everything else still hits the one shared cache.
#
# Maven is pointed at it through webapp/.mvn/maven.config, so a bare `mvn` typed
# by hand in the worktree gets the isolation too — no script needs a flag. The
# main checkout is deliberately left alone on ~/.m2 (as is CI, which caches it).
#
# That same maven.config shortens the Kotlin compile daemon's idle shutdown to
# 900s, because the isolation below has the side effect of giving every worktree
# its own daemon. See the comment on the writer for why.
#
# Runs automatically via the EnterWorktree PostToolUse hook. Manually:
#   bash webapp/scripts/worktree-m2.sh          # this worktree
#   bash webapp/scripts/worktree-m2.sh --all    # every existing worktree
#
# Idempotent: re-running refreshes links (new groupIds in the shared cache get
# linked, links whose target vanished get dropped) and never touches app/mcorg.
# No teardown needed — the directory dies with the worktree, and removing a
# symlink never touches what it points at.

set -euo pipefail

# The one group path that must NOT be shared. Everything else is symlinked.
ISOLATED_GROUP_PATH="app/mcorg"

SHARED_REPO="${MAVEN_SHARED_REPO:-$HOME/.m2/repository}"

usage() {
  echo "Usage: $0 [--all] [<worktree-path>]"
  echo ""
  echo "  --all   Set up every git worktree of this repo (not the main checkout)."
  echo "  <path>  Set up the worktree containing <path> (default: \$PWD)."
  exit 1
}

# --- Build the symlink farm + maven.config for one worktree ----------------
setup_worktree() {
  local worktree_root="$1"
  local local_repo="$worktree_root/webapp/.m2/repository"
  local mvn_dir="$worktree_root/webapp/.mvn"

  mkdir -p "$local_repo"

  # Walk the isolated group path one segment at a time. At each level, link
  # every sibling entry of the shared repo and keep only the segment itself
  # as a real directory.
  local rel_prefix=""
  local segment
  local IFS_SAVE="$IFS"
  IFS='/'
  read -ra SEGMENTS <<< "$ISOLATED_GROUP_PATH"
  IFS="$IFS_SAVE"

  for segment in "${SEGMENTS[@]}"; do
    local shared_dir="$SHARED_REPO${rel_prefix:+/$rel_prefix}"
    local target_dir="$local_repo${rel_prefix:+/$rel_prefix}"
    mkdir -p "$target_dir"

    # Link every entry of the shared level except the segment we descend into.
    if [ -d "$shared_dir" ]; then
      local entry name
      for entry in "$shared_dir"/* "$shared_dir"/.[!.]*; do
        [ -e "$entry" ] || continue
        name="$(basename "$entry")"
        [ "$name" = "$segment" ] && continue
        # A real directory here would be a previously isolated path — leave it.
        if [ -e "$target_dir/$name" ] && [ ! -L "$target_dir/$name" ]; then
          continue
        fi
        ln -sfn "$entry" "$target_dir/$name"
      done
    fi

    # The segment itself must be a real directory, never a link.
    if [ -L "$target_dir/$segment" ]; then
      rm -f "$target_dir/$segment"
    fi
    mkdir -p "$target_dir/$segment"

    rel_prefix="${rel_prefix:+$rel_prefix/}$segment"
  done

  # Drop links whose shared-repo target has since disappeared.
  find "$local_repo" -maxdepth 2 -type l ! -exec test -e {} \; -delete 2>/dev/null || true

  # Point Maven at it, and shorten the Kotlin daemon's idle life. maven.config is
  # read for any `mvn` whose project root is webapp/, which every documented
  # command uses. NOTE: Maven 3.8 feeds this file straight to the CLI parser — a
  # `#` comment line makes it exit 1, so this file holds nothing but the flags.
  #
  # kotlin.daemon.options only works as a system property (a pom <properties>
  # entry is ignored), and it belongs here rather than in the pom because it is a
  # worktree problem: the isolated repo above gives each worktree a distinct
  # compiler classpath, so each gets its OWN Kotlin daemon instead of sharing the
  # main checkout's. At the 7200s default, the daemons of worktrees you finished
  # with hours ago are still resident. 900s reaps them; the main checkout, which
  # has no maven.config, keeps the default.
  mkdir -p "$mvn_dir"
  {
    printf -- '-Dmaven.repo.local=%s\n' "$local_repo"
    printf -- '-Dkotlin.daemon.options=autoshutdownIdleSeconds=900\n'
  } > "$mvn_dir/maven.config"

  echo "worktree-m2: $worktree_root -> $local_repo (isolating $ISOLATED_GROUP_PATH)"
}

# --- Resolve targets --------------------------------------------------------
ALL=false
TARGET_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --all) ALL=true; shift ;;
    -h|--help) usage ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) TARGET_DIR="$1"; shift ;;
  esac
done

# Same stdin handling as worktree-db.sh: the hook passes the worktree in `cwd`.
if [ -z "$TARGET_DIR" ] && [ "$ALL" = false ] && [ ! -t 0 ]; then
  STDIN_JSON="$(cat || true)"
  if [ -n "$STDIN_JSON" ]; then
    TARGET_DIR="$(printf '%s' "$STDIN_JSON" | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("cwd",""))
except Exception: print("")' 2>/dev/null || true)"
  fi
fi
TARGET_DIR="${TARGET_DIR:-$PWD}"

if [ ! -d "$SHARED_REPO" ]; then
  echo "worktree-m2: shared repository $SHARED_REPO does not exist — nothing to link." >&2
  exit 0
fi

MAIN_REPO="$(git -C "$TARGET_DIR" rev-parse --path-format=absolute --git-common-dir | sed 's|/\.git$||')"

if [ "$ALL" = true ]; then
  while read -r wt; do
    [ -n "$wt" ] || continue
    [ "$wt" = "$MAIN_REPO" ] && continue
    setup_worktree "$wt"
  done < <(git -C "$MAIN_REPO" worktree list --porcelain | awk '/^worktree /{print $2}')
  exit 0
fi

WORKTREE_ROOT="$(git -C "$TARGET_DIR" rev-parse --show-toplevel)"
if [ "$WORKTREE_ROOT" = "$MAIN_REPO" ]; then
  echo "worktree-m2: main checkout keeps the shared ~/.m2 — skipping." >&2
  exit 0
fi

setup_worktree "$WORKTREE_ROOT"
