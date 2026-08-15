#!/usr/bin/env bash
#
# Fails if main source writes to stdout/stderr directly instead of going through logback (MCO-342).
#
# Why this exists: ApiProvider.deserializeJson used to `println(e.message)` on the OAuth token
# path, and kotlinx-serialization puts the response body — the access token — in that message
# (MCO-336). `println` bypasses logback entirely, so no level, filter or appender configuration
# could ever have suppressed it. CodeQL's security-extended suite runs `java/sensitive-log` on
# every PR and did *not* flag it, so this grep is not redundant with CodeQL.
#
# Run locally:  bash webapp/scripts/check-no-stdout.sh
set -uo pipefail

cd "$(dirname "$0")/.."

# `cli/` is legitimately stdout-driven: ScoreDiagnostics is a developer diagnostic whose whole
# output is a printed report, and IngestServerFiles is a CLI entry point.
ALLOWLIST='mc-web/src/main/kotlin/app/mcorg/cli/'

PATTERN='(^|[^A-Za-z0-9_.])(println|print)[[:space:]]*\(|System\.(out|err)|\.printStackTrace[[:space:]]*\('

violations="$(
  grep -rInE "$PATTERN" --include='*.kt' \
    mc-*/src/main 2>/dev/null \
    | grep -v "^$ALLOWLIST" \
    || true
)"

if [[ -n "$violations" ]]; then
  echo "::error::Direct stdout/stderr writes found in main source (MCO-342)."
  echo
  echo "$violations"
  echo
  echo "These bypass logback, so no log level, filter or appender can suppress them — which is how"
  echo "an OAuth access token ended up printable in ApiProvider (MCO-336)."
  echo
  echo "Use a logger instead:"
  echo "    private val logger = LoggerFactory.getLogger(Foo::class.java)"
  echo "    logger.warn(\"...\")"
  echo
  echo "and read documentation/logging.md before logging anything derived from an exception"
  echo "message or an upstream response body."
  echo
  echo "Legitimately stdout-driven CLI code belongs under $ALLOWLIST, which is exempt."
  exit 1
fi

echo "OK: no direct stdout/stderr writes in main source (outside $ALLOWLIST)."
