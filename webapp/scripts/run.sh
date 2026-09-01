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
    echo "  --debug-port N   Set debug port (default: derived from the HTTP port; 5005 on 8080)"
    exit 1
}

ENV_NAME="local"
DEBUG=false
SUSPEND=false
# Empty, not 5005: an unset debug port is derived from the HTTP port below, so worktrees do not
# all collide on 5005 the way they used to collide on 8080. An explicit --debug-port still wins.
DEBUG_PORT=""

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

# A worktree builds against its own Maven repository so a sibling worktree's
# `install` cannot replace the app.mcorg jars underneath this one. The hook sets
# this up on EnterWorktree; a hand-made worktree (`claude -w`, `git worktree
# add`) has no hook, so provision it here rather than silently sharing ~/.m2.
if [[ ! -f "$WEBAPP_DIR/.mvn/maven.config" ]]; then
    bash "$SCRIPT_DIR/worktree-m2.sh" "$WEBAPP_DIR" >/dev/null 2>&1 || true
fi

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

# A worktree gets its own HTTP port so two dev servers can run at once (MCO-476). The
# EnterWorktree hook normally allocates it; a hand-made worktree (`claude -w`, `git worktree add`)
# has no hook, so allocate here rather than silently colliding on 8080. The main checkout gets
# nothing back and stays on the default.
#
# Exported BEFORE sourcing so an env file can interpolate it — test.env's APP_HOST does.
if [[ -z "${PORT:-}" ]]; then
    ALLOCATED_PORT="$(bash "$SCRIPT_DIR/worktree-port.sh" "$WEBAPP_DIR" 2>/dev/null || true)"
    if [[ -n "$ALLOCATED_PORT" ]]; then
        export PORT="$ALLOCATED_PORT"
    fi
fi

set -a
source "$ENV_FILE"
set +a

PORT="${PORT:-8080}"

if [[ "$SUSPEND" == true && "$DEBUG" == false ]]; then
    echo "Error: --suspend requires --debug"
    exit 1
fi

# Derived from the HTTP port, so each worktree's debugger port is as distinct as its server port.
DEBUG_PORT="${DEBUG_PORT:-$((5005 + PORT - 8080))}"

MAVEN_OPTS=""
if [[ "$DEBUG" == true ]]; then
    SUSPEND_MODE="n"
    if [[ "$SUSPEND" == true ]]; then
        SUSPEND_MODE="y"
    fi
    MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$SUSPEND_MODE,address=*:$DEBUG_PORT"
    echo "Debug enabled on port $DEBUG_PORT (suspend=$SUSPEND_MODE)"
fi

# Why `install` and not `compile` (MCO-285): `exec:java -pl mc-web` narrows the reactor to one
# module, so mc-domain and the other siblings resolve from ~/.m2 — the last INSTALLED jars,
# which `compile` never updates. Skip the install and you get a NoSuchMethodError at runtime
# somewhere that looks unrelated to what you changed.
#
# `clean` is deliberately NOT here (MCO-378). It was only ever compensating for Kotlin's
# incremental compilation, which is now off in pom.xml — Maven's own staleness check rebuilds
# everything it needs and takes ~13s from cold.
cd "$WEBAPP_DIR"
echo "Building..."
mvn install -DskipTests -q

echo "Starting application with $ENV_NAME environment on http://localhost:$PORT"
MAVEN_OPTS="$MAVEN_OPTS" exec mvn exec:java -pl mc-web
