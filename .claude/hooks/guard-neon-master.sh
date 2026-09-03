#!/bin/bash
# PreToolUse(Bash): refuse any command that opens a data connection to the Neon
# `master` branch — the production-backed database every worktree is forked from.
#
# Why this exists
# ---------------
# Nothing local ever needs a data connection to master. The main checkout talks to
# the localhost Docker postgres; a worktree talks to its own `wt/<branch>` Neon fork.
# Master is a *fork source*, and forking is a control-plane call (`neonctl branches
# create`), not a psql session. So a local psql against master is always either a
# mistake or a deliberate, considered exception.
#
# The hole this closes: `neonctl connection-string master` mints working *write*
# credentials on demand. Nothing else stands between that and a stray UPDATE on
# production data. Neon's protected-branch flag on master guards deletion and would
# enforce an IP allowlist, but no allowlist is configured, so today it does not
# restrict connections at all.
#
# What it blocks
# --------------
#   * any command naming master's endpoint host (see HOST below)
#   * `neonctl connection-string master` and `--branch master` variants, which are
#     how that host gets into a command in the first place
#
# Reads are blocked too, deliberately. Telling a write from a read means parsing SQL
# out of a shell command, which fails open on anything non-obvious — a psql -f, a
# heredoc, a function call. Blocking the connection is the part that can be done
# exactly, and a considered read is one env var away.
#
# Escape hatch, for a deliberate look at production:
#
#     SEAM_ALLOW_NEON_MASTER=1 psql "$(neonctl connection-string master ...)" -c '...'
#
# Setting it is the conscious act this hook exists to require. Prefer exporting it
# for a single command rather than for a shell.
#
# Exit codes: 0 allows, 2 blocks and shows stderr to the model.

set -uo pipefail

# master's compute endpoint. Stable for the life of the endpoint; if Neon ever
# reissues it, `neonctl connection-string master --project-id "$PROJECT"` prints
# the new host and this constant is the one line to update.
HOST="ep-withered-truth-a2d65fv7"
PROJECT="sweet-dust-00910797"

input=$(cat)
command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)

[ -z "$command" ] && exit 0

# An explicit, deliberate override. Checked against the command text rather than the
# hook's own environment: the hook does not inherit the `VAR=value cmd` prefix the
# user would write, so the prefix in the command is what has to be read.
case "$command" in
  *SEAM_ALLOW_NEON_MASTER=1*) exit 0 ;;
esac

reason=""

case "$command" in
  *"$HOST"*)
    reason="it names master's endpoint host ($HOST)"
    ;;
esac

# `neonctl connection-string master` (and --branch master) is how that host is
# obtained; catching it here makes the block land on the command that fetches the
# credentials rather than one pipeline stage later.
if [ -z "$reason" ] && printf '%s' "$command" | grep -Eq 'neonctl[^|;&]*connection-string'; then
  if printf '%s' "$command" | grep -Eq 'connection-string[[:space:]]+master([[:space:]]|$)|--branch[[:space:]]+master([[:space:]]|$)'; then
    reason="it asks neonctl for master's connection string, which mints write credentials"
  fi
fi

[ -z "$reason" ] && exit 0

cat >&2 <<EOF
Blocked: this command connects to the Neon **master** branch — $reason.

master is the production-backed database. Local work never needs a data connection
to it: the main checkout uses the localhost Docker postgres, and a worktree uses its
own wt/<branch> fork. Forking reads master through the control plane, not psql.

If you meant your own worktree's database, read the credentials from the local.env
next to you (it is the single source of truth for which DB you are pointed at):

    set -a && . webapp/local.env && set +a
    psql "\$(printf '%s' "\$DB_URL" | sed 's|^jdbc:||')&user=\$DB_USER&password=\$DB_PASSWORD" -c '\dt'

To confirm which branch a connection actually lands on, compare timelines:

    psql "<url>" -tAc 'SHOW neon.timeline_id;'
    neonctl connection-string wt/<your-branch> --project-id $PROJECT

If you genuinely need to touch production, say so out loud and re-run with the
override prefixed to the command:

    SEAM_ALLOW_NEON_MASTER=1 <your command>
EOF
exit 2
