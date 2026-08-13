#!/usr/bin/env bash
set -euo pipefail

# Runs the Minecraft data ingestion locally — the same pipeline the Fly machine
# (ingest-machine.sh) runs in production, but against your local/worktree DB.
# Invokes app.mcorg.cli.IngestServerFilesKt once and exits. Ledger-driven and
# idempotent: only new/changed versions are downloaded and stored.

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Error: JAVA_HOME is not set"
    exit 1
fi

SCRIPT_DIR="$(dirname "$0")"
WEBAPP_DIR="$SCRIPT_DIR/.."
ENV_FILE="$WEBAPP_DIR/local.env"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Environment file not found: $ENV_FILE"
    echo "Run ./scripts/run.sh once to seed it, or copy local.env.example."
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

echo "Compiling..."
cd "$WEBAPP_DIR"
# `install`, not `compile`: the run below is `-pl mc-web`, which resolves sibling modules
# (mc-data, mc-engine, ...) from ~/.m2 rather than the reactor. With a bare `compile` an
# extraction change lands in mc-data/target/classes, is never installed, and the ingest
# silently runs the *previous* extraction code — looking like the change had no effect.
mvn install -q -DskipTests

echo "Ingesting Minecraft server data into $DB_URL ..."
exec mvn -pl mc-web exec:java@ingest-server-files
