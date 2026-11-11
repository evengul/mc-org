#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Error: JAVA_HOME is not set"
    exit 1
fi

cd "$(dirname "$0")/.."
WEBAPP_DIR="$(pwd)"
# `-pl mc-web` resolves the siblings from ~/.m2: in a cloud session, wait for the background install.
. "$WEBAPP_DIR/scripts/lib/build.sh"
seam_wait_for_cloud_setup

DB_URL="jdbc:postgresql://localhost:5432/postgres" \
DB_USER="postgres" \
DB_PASSWORD="supersecret" \
mvn flyway:migrate -pl mc-web
