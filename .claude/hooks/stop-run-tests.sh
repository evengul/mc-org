#!/bin/bash
# Stop hook: run tests if Kotlin files were modified, feed failures back to Claude

INPUT=$(cat)

# Prevent infinite loop — if Claude is already reacting to a hook, skip
STOP_HOOK_ACTIVE=$(echo "$INPUT" | sed -n 's/.*"stop_hook_active"\s*:\s*\(true\|false\).*/\1/p' | head -1)
if [ "$STOP_HOOK_ACTIVE" = "true" ]; then
  exit 0
fi

# Only run if there are modified/staged Kotlin files (git runs from project root)
MODIFIED_KT=$(git -C "$CLAUDE_PROJECT_DIR" status --porcelain 2>/dev/null | grep -c '\.kt$')
if [ "$MODIFIED_KT" -eq 0 ]; then
  exit 0
fi

echo "Running unit tests ($MODIFIED_KT modified .kt file(s))..."
cd "$CLAUDE_PROJECT_DIR" || exit 0
# Unit tier only — fast, no Docker. Database/integration tiers are run explicitly
# (or by CI); bare `mvn test` is banned because it silently skips database-tagged ITs.
OUTPUT=$(./webapp/scripts/test.sh 2>&1)
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  echo "=== Tests FAILED — last 50 lines ==="
  echo "$OUTPUT" | tail -n 50
  exit 2  # Feed failure back to Claude to fix
fi

# Tests passed — silent success (just let Claude finish)
exit 0
