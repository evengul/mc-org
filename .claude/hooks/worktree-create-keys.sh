#!/bin/bash
# PostToolUse(EnterWorktree): generate the gitignored JWT signing keys in a freshly
# entered/created git worktree so the test suite can run there immediately.
#
# The keys live under mc-web/src/main/resources/keys/ and are .gitignored, so a fresh
# worktree never has them — without this, auth tests (and CreateTokenStep) fail with
# "Could not read key file private_key.pem".
#
# Defensive: scans the tool result for the worktree root (the directory that contains
# webapp/mc-web/create-keys.sh) and no-ops cleanly if it can't be determined. Only the
# tool_response/tool_input are scanned, never the top-level cwd, so it targets the NEW
# worktree rather than the session's original directory.
input=$(cat)

wt=$(printf '%s' "$input" \
  | jq -r '[(.tool_response, .tool_input) | .. | strings] | unique | .[]' 2>/dev/null \
  | while IFS= read -r p; do
      if [ -f "$p/webapp/mc-web/create-keys.sh" ]; then echo "$p"; break; fi
    done)

if [ -n "$wt" ]; then
  ( cd "$wt/webapp/mc-web" && bash create-keys.sh ) >/dev/null 2>&1
fi

exit 0
