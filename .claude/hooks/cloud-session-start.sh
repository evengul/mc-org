#!/bin/bash
# SessionStart hook for Claude Code cloud sessions only: provisions the container
# (webapp/scripts/cloud-setup.sh) and puts JDK 25 on the session's PATH.
# Local sessions exit immediately — they use the worktree setup instead.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

r="${CLAUDE_PROJECT_DIR:-.}"
[ -d "$r/webapp" ] || r="$r/mc-org"

# Before the setup, so a failure in a later step (Docker, migrations) still leaves the
# session on JDK 25 instead of hiding behind a wall of JDK 21 compile errors.
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo 'export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH' >> "$CLAUDE_ENV_FILE"
fi

# --background: returns once Docker and the DB are up; the build and migrations carry on, and the
# scripts that need them wait (MCO-658).
"$r/webapp/scripts/cloud-setup.sh" --background
