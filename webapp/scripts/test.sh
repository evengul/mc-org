#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$0")"
WEBAPP_DIR="$SCRIPT_DIR/.."

usage() {
    echo "Usage: $0 [--database] [--integration] [--exclude-unit-tests]"
    echo ""
    echo "Runs unit tests by default (no Docker required)."
    echo ""
    echo "Options:"
    echo "  --database            Include database tests (requires Docker)"
    echo "  --integration         Include integration tests (requires Docker and the app running)"
    echo "  --exclude-unit-tests  Skip unit tests"
    exit 1
}

DATABASE=false
INTEGRATION=false
UNIT=true

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
        --exclude-unit-tests)
            UNIT=false
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

echo "Compiling..."
mvn test-compile -B

if [[ "$UNIT" == true ]]; then
    echo "Running unit tests..."
    mvn test -B
fi

if [[ "$DATABASE" == true ]]; then
    echo "Running database tests..."
    mvn test -pl mc-web -Dsurefire.excludedGroups= -Dgroups=database -B
fi

if [[ "$INTEGRATION" == true ]]; then
    echo "Running integration tests..."
    mvn failsafe:integration-test failsafe:verify -pl mc-web -B
fi
