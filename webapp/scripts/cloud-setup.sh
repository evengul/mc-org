#!/usr/bin/env bash
# Brings a Claude Code cloud session's container up to "can build, test and run Seam".
#
# A cloud session is not a worktree: the container itself is the isolation, the branch is
# assigned by the session, and the database is a throwaway localhost Docker postgres rather
# than a Neon branch. So none of worktree-db.sh / worktree-port.sh / worktree-m2.sh apply here.
#
# Runs automatically from the SessionStart hook (.claude/hooks/cloud-session-start.sh) and is
# safe to run by hand. Idempotent: every step checks first, so a warm container costs a second.
#
#   webapp/scripts/cloud-setup.sh            # JDK, Maven cache, local.env, Docker DB, migrations
#   webapp/scripts/cloud-setup.sh --ingest   # + Minecraft data, INGEST_VERSIONS (default: latest)
#
# After it, in a fresh shell: export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
# (the hook does this for the session through $CLAUDE_ENV_FILE).
set -euo pipefail

INGEST=false
for arg in "$@"; do
    case "$arg" in
        --ingest) INGEST=true ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 1 ;;
    esac
done

WEBAPP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JDK_DIR=/opt/jdk25
LOG="${TMPDIR:-/tmp}/seam-cloud-setup.log"
: > "$LOG"

step() { echo "cloud-setup: $*"; }
run() { "$@" >>"$LOG" 2>&1 || { echo "cloud-setup: FAILED: $* (log: $LOG)" >&2; tail -20 "$LOG" >&2; exit 1; }; }

# 1. JDK 25 — webapp/pom.xml's java.version. The image ships an older JDK.
if [[ ! -x "$JDK_DIR/bin/java" ]]; then
    step "installing Temurin JDK 25 into $JDK_DIR"
    mkdir -p "$JDK_DIR"
    run bash -c "curl -fsSL 'https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse' | tar -xz -C '$JDK_DIR' --strip-components=1"
fi
export JAVA_HOME="$JDK_DIR" PATH="$JDK_DIR/bin:$PATH"

# 2. Maven cache — `install` so -pl builds resolve siblings (MCO-285). Re-run only when a pom
#    or this script changed; the hash lives in ~/.m2 so it disappears with the cache.
hashes="$HOME/.m2/.seam-cloud-setup.sha256"
if ! (cd "$WEBAPP_DIR" && sha256sum --check --status "$hashes" 2>/dev/null); then
    rm -f "$hashes"
    step "building and installing all modules (warms ~/.m2; ~2 min cold)"
    (cd "$WEBAPP_DIR" && run mvn -q -B install -DskipTests)
    (cd "$WEBAPP_DIR" && sha256sum scripts/cloud-setup.sh pom.xml */pom.xml > "$hashes")
fi

# 3. local.env — the template's localhost DB, with docker-compose-local.yaml's password.
if [[ ! -f "$WEBAPP_DIR/local.env" ]]; then
    step "seeding local.env from local.env.example"
    sed 's/^DB_PASSWORD=$/DB_PASSWORD=supersecret/' "$WEBAPP_DIR/local.env.example" > "$WEBAPP_DIR/local.env"
fi

# 4. Docker + the local postgres container.
if ! docker info >/dev/null 2>&1; then
    step "starting Docker"
    # A resumed session keeps the filesystem but not the processes, so the previous dockerd's
    # pid file survives and the init script takes it for a running daemon and does nothing.
    pid=$(cat /var/run/docker.pid 2>/dev/null || true)
    if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
        rm -f /var/run/docker.pid
    fi
    # Not `service docker start`: its init script cannot raise ulimit in the sandbox and leaves
    # no daemon behind. The platform starts dockerd on a fresh container; this covers a resume.
    setsid nohup dockerd >/var/log/dockerd-cloud-setup.log 2>&1 < /dev/null &
    for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
    docker info >/dev/null 2>&1 || { echo "cloud-setup: Docker did not start (log: /var/log/dockerd-cloud-setup.log)" >&2; exit 1; }
fi
if [[ "$(docker inspect -f '{{.State.Running}}' mcorg-db 2>/dev/null)" != "true" ]]; then
    step "starting the local postgres container"
    run "$WEBAPP_DIR/scripts/start-db.sh"
fi
export PGPASSWORD=supersecret
for _ in $(seq 1 30); do psql -h localhost -U postgres -tAc 'select 1' >/dev/null 2>&1 && break; sleep 1; done

# 5. Migrations — only when the DB is behind the files on disk.
on_disk=$(ls "$WEBAPP_DIR"/mc-web/src/main/resources/db/migration/V*.sql | wc -l)
applied=$(psql -h localhost -U postgres -tAc \
    "select count(*) from flyway_schema_history where success and version is not null" 2>/dev/null || echo 0)
if [[ "$applied" -lt "$on_disk" ]]; then
    step "applying migrations ($applied of $on_disk applied)"
    run "$WEBAPP_DIR/scripts/migrate-locally.sh"
fi

# 6. Minecraft data, opt-in. Ledger-driven, so a re-run for an ingested version is a no-op.
if [[ "$INGEST" == true ]]; then
    export INGEST_VERSIONS="${INGEST_VERSIONS:-latest}"
    step "ingesting Minecraft data (INGEST_VERSIONS=$INGEST_VERSIONS)"
    run "$WEBAPP_DIR/scripts/ingest-locally.sh"
fi

step "ready (JAVA_HOME=$JDK_DIR, DB localhost:5432, $on_disk migrations)"
