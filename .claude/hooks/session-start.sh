#!/bin/bash
# SessionStart hook: re-inject project context after compaction or resume
# Only fires on compact/resume (matched by settings.json)

BRANCH=$(git -C "$CLAUDE_PROJECT_DIR" branch --show-current 2>/dev/null || echo "unknown")
LAST_MIGRATION=$(ls "$CLAUDE_PROJECT_DIR"/webapp/src/main/resources/db/migration/*.sql 2>/dev/null | sort | tail -1 | xargs basename 2>/dev/null || echo "unknown")

cat <<EOF
=== Session Context Restored ===
Branch: $BRANCH
Last migration: $LAST_MIGRATION
Stack: Ktor + Kotlin, Maven (NOT Gradle), PostgreSQL + Flyway, HTMX + Kotlin HTML DSL
Key rules: HTML-only (no JSON), auth in plugins not pipelines, import kotlinx.html.stream.createHTML
Skills: /docs-development /docs-architecture /docs-css /docs-htmx /docs-business /docs-troubleshoot
EOF
