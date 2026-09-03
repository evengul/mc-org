#!/usr/bin/env bash
# Install the playwright-cli shim, so every worktree drives its own browser.
#
# See playwright-cli-shim.sh for why the shim exists at all. This script does
# the two things around it: put it on PATH, and clean up after worktrees that
# are gone.
#
#   bash webapp/scripts/worktree-playwright.sh           # install / refresh
#   bash webapp/scripts/worktree-playwright.sh --prune   # stop dead sessions
#   bash webapp/scripts/worktree-playwright.sh --status  # show what is wired up
#
# Install is idempotent and machine-level, not per-worktree: one symlink at
# ~/.local/bin/playwright-cli pointing at the MAIN CHECKOUT's copy of the shim.
# It deliberately does not point into a worktree — worktrees get deleted, and a
# dangling shim would break playwright-cli everywhere on the box. Re-running
# from any worktree refreshes the same one link.
#
# ~/.local/bin is already first on PATH (exported by ~/.bashrc, which Claude
# Code sources when it builds its shell snapshot), so nothing edits a profile.
#
# Runs automatically via the EnterWorktree PostToolUse hook; --prune runs on
# ExitWorktree, the same shape as worktree-db-cleanup.sh --prune.

set -euo pipefail

SHIM_NAME="playwright-cli"
INSTALL_DIR="${PLAYWRIGHT_SHIM_BIN_DIR:-$HOME/.local/bin}"
INSTALL_PATH="$INSTALL_DIR/$SHIM_NAME"

# The binary to drive sessions with: the installed shim when it is there, else
# whatever is on PATH. Prune runs on ExitWorktree, after install, but should
# still do something sensible on a box where install never ran.
cli() {
  if [ -x "$INSTALL_PATH" ]; then
    echo "$INSTALL_PATH"
  else
    command -v "$SHIM_NAME" 2>/dev/null || echo "$SHIM_NAME"
  fi
}

usage() {
  echo "Usage: $0 [--prune|--status]"
  echo ""
  echo "  (no args)  Install or refresh the ~/.local/bin/playwright-cli shim."
  echo "  --prune    Stop and delete browser sessions whose worktree is gone."
  echo "  --status   Report the shim, this directory's session, and live sessions."
  exit 1
}

# --- Locate the main checkout ------------------------------------------------
# --git-common-dir points at the main repo's .git from inside a worktree too,
# which is exactly what we need: the symlink must target the main checkout.
main_checkout() {
  local common
  common=$(git rev-parse --git-common-dir 2>/dev/null) || return 1
  common=$(cd "$common" 2>/dev/null && pwd -P) || return 1
  dirname "$common"
}

repo_name() {
  basename "$(main_checkout)"
}

# The symlink target. Always the main checkout's copy, never a worktree's — a
# worktree gets deleted and the shim would dangle, breaking playwright-cli
# everywhere on the box. PLAYWRIGHT_SHIM_SOURCE overrides it, which is how you
# bootstrap from a branch whose shim has not reached the main checkout yet.
shim_source() {
  echo "${PLAYWRIGHT_SHIM_SOURCE:-$(main_checkout)/webapp/scripts/playwright-cli-shim.sh}"
}

# The name a given directory maps to. Asks the shim rather than reimplementing
# the rule — two copies of it would drift, and a drifted name prunes live work.
session_in_dir() {
  ( cd "$1" 2>/dev/null && PLAYWRIGHT_CLI_SHIM_PRINT_SESSION=1 bash "$(shim_source)" 2>/dev/null ) || true
}

# --- Install -----------------------------------------------------------------
install_shim() {
  local src
  src=$(shim_source)

  if [ ! -f "$src" ]; then
    echo "ERROR: shim not found at $src" >&2
    echo "  The symlink must target the MAIN CHECKOUT's copy, and that checkout" >&2
    echo "  does not have it — most likely the branch adding it has not merged." >&2
    echo "  To bootstrap from this branch anyway:" >&2
    echo "    PLAYWRIGHT_SHIM_SOURCE=\$PWD/webapp/scripts/playwright-cli-shim.sh $0" >&2
    exit 1
  fi
  chmod +x "$src" 2>/dev/null || true
  mkdir -p "$INSTALL_DIR"

  if [ -e "$INSTALL_PATH" ] && [ ! -L "$INSTALL_PATH" ]; then
    # A real file here is somebody's actual npm install, not ours to replace.
    echo "ERROR: $INSTALL_PATH exists and is a regular file, not a symlink." >&2
    echo "  Refusing to overwrite it. Move it aside and re-run if you want the shim." >&2
    exit 1
  fi

  local current=""
  [ -L "$INSTALL_PATH" ] && current=$(readlink -f "$INSTALL_PATH" 2>/dev/null || true)

  if [ "$current" = "$(readlink -f "$src")" ]; then
    echo "playwright-cli shim already installed: $INSTALL_PATH -> $src"
  else
    ln -sfn "$src" "$INSTALL_PATH"
    echo "playwright-cli shim installed: $INSTALL_PATH -> $src"
  fi

  check_path_order
}

# The shim only works if it is found BEFORE the real playwright-cli. If PATH
# ever changes order this fails silently — every worktree quietly shares one
# browser again — so say so loudly at install time rather than never.
check_path_order() {
  local resolved
  resolved=$(command -v "$SHIM_NAME" 2>/dev/null || true)

  if [ -z "$resolved" ]; then
    echo "  WARNING: '$SHIM_NAME' is not on PATH at all."
    echo "           Add $INSTALL_DIR to PATH in ~/.bashrc."
    return
  fi
  if [ "$(readlink -f "$resolved")" != "$(readlink -f "$INSTALL_PATH")" ]; then
    echo "  WARNING: '$SHIM_NAME' resolves to $resolved, not the shim."
    echo "           $INSTALL_DIR must come BEFORE that directory on PATH,"
    echo "           otherwise every worktree shares one browser again."
    return
  fi
  echo "  PATH order OK — '$SHIM_NAME' resolves to the shim."
}

# --- Prune -------------------------------------------------------------------
# Stop and delete sessions in this repo's namespace whose worktree is gone.
#
# Built from the set of names the live worktrees CURRENTLY derive, not by
# reversing a session name back into a directory. Names can be truncated and
# hashed when long, so reversing would fail to match a live worktree and prune
# a browser somebody is using.
prune_sessions() {
  local repo expected=() live_dirs=() name dir

  repo=$(repo_name)

  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    live_dirs+=("$dir")
    name=$(session_in_dir "$dir")
    [ -n "$name" ] && expected+=("$name")
  done < <(git worktree list --porcelain | awk '/^worktree /{print substr($0, 10)}')

  echo "Live worktrees: ${#live_dirs[@]}; sessions to keep: ${expected[*]:-(none)}"

  local pruned=0
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    # Only ever touch this repo's own namespace.
    case "$name" in
      "$repo"|"$repo"--*) ;;
      *) continue ;;
    esac

    local keep=0
    for n in ${expected[@]+"${expected[@]}"}; do
      [ "$n" = "$name" ] && keep=1 && break
    done
    [ "$keep" = 1 ] && continue

    echo "  pruning orphaned session: $name"
    "$(cli)" session-stop "$name" >/dev/null 2>&1 || true
    "$(cli)" session-delete "$name" >/dev/null 2>&1 || true
    pruned=$((pruned + 1))
  done < <(list_session_names)

  echo "Pruned $pruned orphaned session(s)."
}

# Parse `session-list` rather than globbing the daemon cache, so the cache
# layout stays playwright-cli's business.  Lines look like "  [stopped] name"
# or "  [running] name - v0.0.61, needs restart".
list_session_names() {
  "$(cli)" session-list 2>/dev/null \
    | sed -n 's/^ *\[\(running\|stopped\)\] \([^ ]*\).*$/\2/p'
}

# --- Status ------------------------------------------------------------------
show_status() {
  local resolved
  resolved=$(command -v "$SHIM_NAME" 2>/dev/null || echo "(not on PATH)")
  echo "shim source : $(shim_source)"
  echo "installed at: $INSTALL_PATH -> $(readlink "$INSTALL_PATH" 2>/dev/null || echo '(missing)')"
  echo "PATH resolves to: $resolved"
  echo "this directory  : $(PLAYWRIGHT_CLI_SHIM_PRINT_SESSION=1 bash "$(shim_source)")"
  echo ""
  "$(cli)" session-list 2>/dev/null || echo "(could not list sessions)"
}

case "${1:-}" in
  "")        install_shim ;;
  --prune)   prune_sessions ;;
  --status)  show_status ;;
  -h|--help) usage ;;
  *)         usage ;;
esac
