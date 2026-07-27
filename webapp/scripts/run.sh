#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Error: JAVA_HOME is not set"
    exit 1
fi

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
    echo "  --fast           Skip the clean rebuild and reuse whatever is already built."
    echo "                   Only safe when nothing outside mc-web has changed — see below."
    exit 1
}

ENV_NAME="local"
DEBUG=false
SUSPEND=false
DEBUG_PORT=5005
FAST=false

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
        --fast)
            FAST=true
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
    if [[ "$ENV_NAME" == "local" && -f "$WEBAPP_DIR/local.env.example" ]]; then
        cp "$WEBAPP_DIR/local.env.example" "$ENV_FILE"
        echo "Seeded $ENV_FILE from local.env.example."
        echo "Set DB_PASSWORD (see docker-compose-local.yaml) and any other blanks, then re-run."
        exit 1
    fi
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

# Why `clean install` and not `compile` (MCO-285):
#
#   * `exec:java -pl mc-web` narrows the reactor to one module, so mc-domain and the other
#     siblings resolve from ~/.m2 — the last INSTALLED jars, which `compile` never updates.
#   * Kotlin's incremental compilation does not propagate an ABI change across module
#     boundaries. After an mc-domain change it rebuilds only the files that textually changed
#     and leaves stale callers in mc-web/target/classes — one was five weeks old when this bit
#     us. `install` alone does not fix that; only `clean` does.
#
# Both failures surface at runtime as NoSuchMethodError / NoClassDefFoundError somewhere that
# looks unrelated to what you changed, which is why the default is the safe one.
cd "$WEBAPP_DIR"
if [[ "$FAST" == true ]]; then
    echo "Compiling (fast: reusing existing build)..."
    mvn compile -q
else
    echo "Building (clean)... use --fast to skip when only mc-web changed"
    mvn clean install -DskipTests -q
fi

echo "Starting application with $ENV_NAME environment..."
MAVEN_OPTS="$MAVEN_OPTS" exec mvn exec:java -pl mc-web
