#!/bin/bash
# PreToolUse hook: block dangerous bash commands before they execute
# Exit 2 = block + send reason to Claude

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | grep -oP '"command"\s*:\s*"\K[^"]+' | head -1)

if [ -z "$COMMAND" ]; then
  exit 0
fi

# Destructive git operations
if echo "$COMMAND" | grep -qE 'git\s+push\s+(-f|--force)'; then
  echo "Blocked: force push can overwrite remote history. Use explicit --force-with-lease or ask the user first." >&2
  exit 2
fi

if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
  echo "Blocked: git reset --hard discards uncommitted changes permanently. Ask the user before proceeding." >&2
  exit 2
fi

if echo "$COMMAND" | grep -qE 'git\s+clean\s+.*-f'; then
  echo "Blocked: git clean -f deletes untracked files. Ask the user before proceeding." >&2
  exit 2
fi

# Recursive deletion
if echo "$COMMAND" | grep -qE 'rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r)\s'; then
  echo "Blocked: rm -rf is irreversible. Identify exactly what to delete and use a safer approach." >&2
  exit 2
fi

# Destructive SQL (outside of migrations)
if echo "$COMMAND" | grep -qiE '(DROP\s+(TABLE|DATABASE|SCHEMA)|TRUNCATE\s+TABLE)'; then
  echo "Blocked: destructive SQL detected. Write a proper Flyway migration instead." >&2
  exit 2
fi

exit 0
