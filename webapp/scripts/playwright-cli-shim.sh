#!/usr/bin/env bash
# A PATH shim for `playwright-cli` that gives every git worktree its own browser.
#
# The problem: playwright-cli derives BOTH the daemon socket
#   /tmp/playwright-cli/<installHash>/<session>.sock
# and the browser profile
#   ~/.cache/ms-playwright/daemon/<installHash>/ud-<session>-<browser>
# from one session name, resolved in mcp/terminal/program.js as
#   --session=X  ->  $PLAYWRIGHT_CLI_SESSION  ->  "default"
# Nothing set either, so every worktree fell through to "default" and drove the
# SAME Chromium — same cookie jar, same tabs, same clicks. A /verify run in one
# worktree navigated another worktree's app and looked entirely plausible doing
# it. The daemon is also spawned with `cwd: process.cwd() // Will be used as
# root`, so a shared daemon is rooted in whichever worktree opened it first.
#
# Why a shim and not documentation: the `--session=X` flag only works for the
# FIRST command against a session. `session` is a member of program.js's
# `globalArgs`, and SessionManager.run exits 1 if any global arg is passed to a
# session that already exists ("The session is already configured"). The env var
# does not populate args.session, so it is the only mechanism that survives
# repeated use. That is what this shim sets.
#
# Why a shim and not local.env: ports and databases are isolated because
# something reads local.env in between — run.sh, readConfig(), migrate-worktree.
# playwright-cli is invoked bare, so there was no in-between to put the
# isolation in. This shim is that missing middle.
#
# Installed as a symlink at ~/.local/bin/playwright-cli by worktree-playwright.sh
# (~/.local/bin is already first on PATH via .bashrc, so no profile edit). The
# symlink points at the MAIN CHECKOUT's copy of this file, so it survives any
# individual worktree being deleted.
#
# Overrides, both respected:
#   PLAYWRIGHT_CLI_SESSION=foo playwright-cli ...   # pin a session by hand
#   playwright-cli --session=foo ...                # flag still wins (first call)
#
# To ask which session a directory maps to, without running anything:
#   PLAYWRIGHT_CLI_SHIM_PRINT_SESSION=1 playwright-cli
# worktree-playwright.sh --prune uses this so the naming rule lives in exactly
# one place; a name it computed differently would prune a live worktree.

set -uo pipefail

# --- Find the real playwright-cli, without recursing into ourselves ----------
# Walk PATH and skip any candidate that resolves to this same file. Resolving
# rather than string-matching is what makes the symlink safe: ~/.local/bin's
# entry resolves to this script and is skipped, the nvm one is not. Never
# hardcode the nvm path — it carries a node version that changes.
resolve_real_cli() {
  local self candidate dir
  self=$(readlink -f "${BASH_SOURCE[0]}" 2>/dev/null) || return 1

  local IFS=:
  for dir in $PATH; do
    [ -n "$dir" ] || continue
    candidate="$dir/playwright-cli"
    [ -x "$candidate" ] || continue
    [ "$(readlink -f "$candidate" 2>/dev/null)" = "$self" ] && continue
    printf '%s' "$candidate"
    return 0
  done
  return 1
}

# --- Derive a session name from the git worktree ----------------------------
# Main checkout       -> mc-org
# .../worktrees/foo   -> mc-org--foo
# Outside any repo    -> (nothing; playwright-cli's own "default" applies)
#
# --git-common-dir points at the MAIN repo's .git even from inside a worktree,
# which is what tells the two cases apart and what supplies the repo prefix.
# The prefix matters because this shim is on PATH for every repo on the box,
# not just this one, and worktree basenames collide across repos.
derive_session_name() {
  local top common repo_root repo name

  top=$(git rev-parse --show-toplevel 2>/dev/null) || return 1
  [ -n "$top" ] || return 1
  common=$(git rev-parse --git-common-dir 2>/dev/null) || return 1
  [ -n "$common" ] || return 1

  # --git-common-dir may come back relative to $PWD; absolutise before dirname.
  common=$(cd "$common" 2>/dev/null && pwd -P) || return 1
  repo_root=$(dirname "$common")
  repo=$(basename "$repo_root")
  top=$(cd "$top" 2>/dev/null && pwd -P) || return 1

  if [ "$top" = "$repo_root" ]; then
    name="$repo"
  else
    name="$repo--$(basename "$top")"
  fi

  # Session names land in a filename (<name>.sock) and a directory name
  # (ud-<name>-<browser>), so keep them to characters that are safe in both.
  name=$(printf '%s' "$name" | tr -c 'A-Za-z0-9._-' '-')

  # The socket lives at /tmp/playwright-cli/<16 hex>/<name>.sock and a unix
  # socket path is capped at 108 bytes; that leaves ~65 for the name. Cap well
  # short of it, and keep truncated names distinct with a hash of the full path
  # rather than letting two long branch names collapse onto one browser.
  if [ "${#name}" -gt 48 ]; then
    local digest
    digest=$(printf '%s' "$top" | sha1sum | cut -c1-6)
    name="${name:0:41}-${digest}"
  fi

  printf '%s' "$name"
}

# Introspection: print the name this directory maps to and stop. Deliberately
# before the real-binary lookup, so it answers even where playwright-cli is not
# installed at all.
if [ -n "${PLAYWRIGHT_CLI_SHIM_PRINT_SESSION:-}" ]; then
  derive_session_name || true
  echo
  exit 0
fi

real_cli="${PLAYWRIGHT_CLI_REAL:-$(resolve_real_cli)}"
if [ -z "$real_cli" ]; then
  echo "playwright-cli shim: cannot find the real playwright-cli on PATH." >&2
  echo "  Install it with: npm install -g @playwright/cli" >&2
  echo "  (this shim is $(readlink -f "${BASH_SOURCE[0]}" 2>/dev/null))" >&2
  exit 127
fi

# Only derive when the caller has not already chosen. An explicit env var wins;
# so does an explicit --session flag, which takes precedence inside
# playwright-cli itself, so we simply leave it alone.
if [ -z "${PLAYWRIGHT_CLI_SESSION:-}" ]; then
  session=$(derive_session_name) || session=""
  [ -n "$session" ] && export PLAYWRIGHT_CLI_SESSION="$session"
fi

# Google Chrome is not installed on this machine, but playwright-cli's
# defaultConfig is browserName "chromium" with launchOptions.channel "chrome" —
# so the out-of-the-box default fails to launch. That is what the playwright
# skill's "run `config --browser=chromium` once per session" step works around,
# and with a session per worktree that step would become once per worktree.
# Setting it here retires the step. The daemon is spawned without an explicit
# env, so it inherits this. Not clobbered if the caller set it.
: "${PLAYWRIGHT_MCP_BROWSER:=chromium}"
export PLAYWRIGHT_MCP_BROWSER

exec "$real_cli" "$@"
