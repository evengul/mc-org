#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$0")"
WEBAPP_DIR="$SCRIPT_DIR/.."

usage() {
    echo "Usage: $0 [--database] [--integration]"
    echo ""
    echo "Runs unit tests by default (no Docker required)."
    echo ""
    echo "Options:"
    echo "  --database      Include database tests (requires Docker)"
    echo "  --integration   Include integration tests (requires Docker and the app running)"
    exit 1
}

DATABASE=false
INTEGRATION=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --database)
            DATABASE=true
            shift
            ;;
        --integration)
            INTEGRATION=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

cd "$WEBAPP_DIR"

if [[ ! -f mc-web/src/main/resources/keys/private_key.pem ]]; then
    echo "Generating JWT signing keys..."
    (cd mc-web && bash create-keys.sh)
fi

echo "Running unit tests..."
mvn test -B

if [[ "$DATABASE" == true ]]; then
    echo "Running database tests..."
    mvn test -pl mc-web -Dsurefire.excludedGroups= -Dgroups=database -B
fi

if [[ "$INTEGRATION" == true ]]; then
    echo "Running integration tests..."
    mvn failsafe:integration-test failsafe:verify -pl mc-web -B
fi
