#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$HOME/.jdks/temurin-21.0.10"

cd "$(dirname "$0")/.."

DB_URL="jdbc:postgresql://localhost:5432/postgres" \
DB_USER="postgres" \
DB_PASSWORD="supersecret" \
mvn flyway:migrate
