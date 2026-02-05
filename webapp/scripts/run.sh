#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="$HOME/.jdks/temurin-21.0.10"

SCRIPT_DIR="$(dirname "$0")"
WEBAPP_DIR="$SCRIPT_DIR/.."

usage() {
    echo "Usage: $0 [--env local|microsoft|test] [--debug] [--debug-port PORT]"
    echo ""
    echo "Options:"
    echo "  --env ENV        Environment file to use (default: local)"
    echo "                     local      - local.env (skips Microsoft sign-in)"
    echo "                     microsoft  - local_microsoft.env (real Microsoft auth)"
    echo "                     test       - test.env (test environment)"
    echo "  --debug          Enable JVM remote debug on port 5005"
    echo "  --suspend        Wait for debugger to attach before starting (requires --debug)"
    echo "  --debug-port N   Set debug port (default: 5005)"
    exit 1
}

ENV_NAME="local"
DEBUG=false
SUSPEND=false
DEBUG_PORT=5005

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)
            ENV_NAME="$2"
            shift 2
            ;;
        --debug)
            DEBUG=true
            shift
            ;;
        --suspend)
            SUSPEND=true
            shift
            ;;
        --debug-port)
            DEBUG_PORT="$2"
            shift 2
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

case "$ENV_NAME" in
    local)      ENV_FILE="$WEBAPP_DIR/local.env" ;;
    microsoft)  ENV_FILE="$WEBAPP_DIR/local_microsoft.env" ;;
    test)       ENV_FILE="$WEBAPP_DIR/test.env" ;;
    *)
        echo "Unknown environment: $ENV_NAME"
        echo "Valid options: local, microsoft, test"
        exit 1
        ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Environment file not found: $ENV_FILE"
    exit 1
fi

set -a
source "$ENV_FILE"
set +a

if [[ "$SUSPEND" == true && "$DEBUG" == false ]]; then
    echo "Error: --suspend requires --debug"
    exit 1
fi

MAVEN_OPTS=""
if [[ "$DEBUG" == true ]]; then
    SUSPEND_MODE="n"
    if [[ "$SUSPEND" == true ]]; then
        SUSPEND_MODE="y"
    fi
    MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$SUSPEND_MODE,address=*:$DEBUG_PORT"
    echo "Debug enabled on port $DEBUG_PORT (suspend=$SUSPEND_MODE)"
fi

echo "Compiling..."
cd "$WEBAPP_DIR"
mvn compile -q

echo "Starting application with $ENV_NAME environment..."
MAVEN_OPTS="$MAVEN_OPTS" exec mvn exec:java
