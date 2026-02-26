#!/bin/bash
# PostToolUse hook: auto-apply Flyway migration when a .sql file is written to db/migration/

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | grep -oP '"file_path"\s*:\s*"\K[^"]+' | head -1)

if [[ "$FILE_PATH" != */db/migration/*.sql ]]; then
  exit 0
fi

echo "Migration file written: $(basename "$FILE_PATH")"
echo "Running mvn flyway:migrate..."

cd "$CLAUDE_PROJECT_DIR/webapp" || exit 0
OUTPUT=$(mvn flyway:migrate 2>&1)
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  echo "Migration FAILED:"
  echo "$OUTPUT" | tail -30
  exit 2  # Send failure back to Claude to fix
fi

echo "Migration applied successfully."
echo "$OUTPUT" | grep -E '(Successfully applied|Current version|No migration necessary)' | head -5
