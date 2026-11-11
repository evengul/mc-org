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
#   webapp/scripts/cloud-setup.sh --seed     # + --ingest, then the demo user's "Demo World" (MCO-657)
#   webapp/scripts/cloud-setup.sh --background   # the hook's: build + migrate after returning
#
# Two phases (MCO-658). The first (JDK, keys, local.env, Docker, DB) is quick and always runs in
# the foreground. The second (mvn install, migrations, ingest, seed) is most of a cold start;
# with --background it carries on after the script returns, so the session is usable in seconds.
# It holds $SEAM_CLOUD_LOCK until done, and run.sh, test.sh, ingest-locally.sh and
# migrate-locally.sh wait on it (lib/build.sh), failing if it failed. Its outcome is in
# $SEAM_CLOUD_STATUS ("ok", "running", or "failed at: <step>").
#
# After it, in a fresh shell: export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
# (the hook does this for the session through $CLAUDE_ENV_FILE).
set -euo pipefail

INGEST=false
SEED=false
BACKGROUND=false
PHASE_B_ONLY=false
for arg in "$@"; do
    case "$arg" in
        --ingest) INGEST=true ;;
        # The seed's resource lines name items, so it needs a version's data first.
        --seed) INGEST=true; SEED=true ;;
        --background) BACKGROUND=true ;;
        # Internal: the detached second phase, which inherits the lock on fd 9.
        --phase-b) PHASE_B_ONLY=true ;;
        -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 1 ;;
    esac
done

WEBAPP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/build.sh
. "$WEBAPP_DIR/scripts/lib/build.sh"
JDK_DIR=/opt/jdk25
LOG="$SEAM_CLOUD_LOG"
CURRENT_STEP=""
# Appended, not truncated: the SessionStart run's log is the record of the cold start, and a later
# `--ingest` or `--seed` by hand would otherwise wipe it. Each run starts with a dated header.
[[ "$PHASE_B_ONLY" == true ]] || echo "=== $(date -u +%FT%TZ) cloud-setup.sh $* ===" >> "$LOG"

step() { CURRENT_STEP="$*"; echo "cloud-setup: $*"; }
# 9>&-: a command (and anything it leaves running) must not inherit cloud-setup's lock. On a
# function, bash keeps a private copy for the duration, so the lock itself stays held.
run() { "$@" 9>&- >>"$LOG" 2>&1 || { echo "cloud-setup: FAILED: $* (log: $LOG)" >&2; tail -20 "$LOG" >&2; exit 1; }; }


# Phase B: everything that needs Maven. In the foreground, or detached with --background.
phase_b() {
    # Whatever ends this phase, the status says so: the scripts that waited read it.
    trap 'rc=$?; if [[ $rc == 0 ]]; then echo ok > "$SEAM_CLOUD_STATUS"; else echo "failed at: $CURRENT_STEP" > "$SEAM_CLOUD_STATUS"; fi' EXIT

    # 5. Modules into ~/.m2 — `install` so -pl builds resolve siblings (MCO-285). lib/build.sh
    #    skips it when nothing changed since the last install, so a resume costs nothing, and
    #    run.sh and ingest-locally.sh after it skip too.
    step "mvn install (skipped when nothing changed since the last; ~2 min cold)"
    run seam_install

    # 6. Migrations — only when the applied versions differ from the files on disk. Compares the
    #    sets, not counts, so a renumbered migration is not missed. A DB that has a version the
    #    branch lacks makes flyway fail validation: that is reported, not papered over.
    local applied_versions
    applied_versions=$(psql -h localhost -U postgres -tAc \
        "select version from flyway_schema_history where success and version is not null" 2>/dev/null | sort || true)
    if [[ "$applied_versions" != "$ON_DISK_VERSIONS" ]]; then
        step "applying migrations ($(echo "$applied_versions" | grep -c . || true) applied, $ON_DISK on disk)"
        run "$WEBAPP_DIR/scripts/migrate-locally.sh"
    fi

    # 7. Minecraft data, opt-in. Ledger-driven, so a re-run for an ingested version is a no-op.
    if [[ "$INGEST" == true ]]; then
        export INGEST_VERSIONS="${INGEST_VERSIONS:-latest}"
        step "ingesting Minecraft data (INGEST_VERSIONS=$INGEST_VERSIONS)"
        run "$WEBAPP_DIR/scripts/ingest-locally.sh"
    fi

    # 8. The demo world, opt-in (MCO-657). Idempotent: an existing "Demo World" is left alone.
    #    The install above (or the ingest's) makes the -pl run see current code.
    if [[ "$SEED" == true ]]; then
        step "seeding the demo user's world"
        (cd "$WEBAPP_DIR" && set -a && . ./local.env && set +a && run mvn -q -B -pl mc-web exec:java@seed-demo)
        # This run's line is the last one: the log is appended to. `|| true`: a missing line is not
        # worth failing a seed that succeeded.
        grep '^seed-demo:' "$LOG" | tail -1 || true
    fi
}

ON_DISK_VERSIONS=$(ls "$WEBAPP_DIR"/mc-web/src/main/resources/db/migration/ \
    | sed -n 's/^V\([0-9_]*\)__.*\.sql$/\1/p' | tr '_' '.' | sort)
ON_DISK=$(echo "$ON_DISK_VERSIONS" | grep -c . || true)
export PGPASSWORD=supersecret

if [[ "$PHASE_B_ONLY" == true ]]; then
    export JAVA_HOME="$JDK_DIR" PATH="$JDK_DIR/bin:$PATH"
    phase_b
    step "background build done in ${SECONDS}s"
    exit 0
fi

# One cloud-setup at a time, and the scripts that use lib/build.sh wait for it. A detached second
# phase inherits fd 9, so the lock outlives this script until the build is done.
exec 9>"$SEAM_CLOUD_LOCK"
if ! flock -n -x 9; then
    # A second SessionStart (compact, clear) while the first's build runs: that build is already
    # doing everything this would, and waiting here would hold the session for the whole of it.
    if [[ "$BACKGROUND" == true ]]; then
        step "a background build is already running; nothing to do (progress: tail -f $LOG)"
        exit 0
    fi
    step "waiting for a cloud-setup that is already running (log: $LOG)"
    flock -x 9
fi
# A failure in the quick phase is recorded too, so nothing that waits later reads an older "ok".
trap 'rc=$?; [[ $rc == 0 ]] || echo "failed at: $CURRENT_STEP" > "$SEAM_CLOUD_STATUS"' EXIT
# The stamp the install used before MCO-658; lib/build.sh keeps its own.
rm -f "$HOME/.m2/.seam-cloud-setup.sha256"
# Its children (ingest-locally.sh, migrate-locally.sh) must not wait for the lock it holds.
export SEAM_CLOUD_SETUP_RUNNING=1

# Phase A: quick, and needed before anything else.

# 1. JDK 25 — webapp/pom.xml's java.version. The image ships an older JDK.
if [[ ! -x "$JDK_DIR/bin/java" ]]; then
    step "installing Temurin JDK 25 into $JDK_DIR"
    # Extract beside the target and move it into place, so an interrupted download never
    # leaves a $JDK_DIR with a bin/java that the check above would take for a working JDK.
    tmp="$(mktemp -d /opt/jdk25.partial.XXXXXX)"
    run bash -o pipefail -c "curl -fsSL 'https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse' | tar -xz -C '$tmp' --strip-components=1"
    run "$tmp/bin/java" -version
    rm -rf "$JDK_DIR" && mv "$tmp" "$JDK_DIR"
fi
export JAVA_HOME="$JDK_DIR" PATH="$JDK_DIR/bin:$PATH"

# 2. JWT signing keys — gitignored, so a fresh clone has none, and without them the demo sign-in
#    answers 500 and screenshot.js exits 3. Before the build: they are read off the classpath.
if [[ ! -f "$WEBAPP_DIR/mc-web/src/main/resources/keys/private_key.pem" ]]; then
    step "generating JWT signing keys"
    (cd "$WEBAPP_DIR/mc-web" && run bash create-keys.sh)
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
    # Check the name, not just liveness: after a resume the old pid can belong to anything.
    if [[ -n "$pid" ]] && [[ "$(cat /proc/"$pid"/comm 2>/dev/null)" != "dockerd" ]]; then
        rm -f /var/run/docker.pid
    fi
    # Not `service docker start`: its init script cannot raise ulimit in the sandbox and leaves
    # no daemon behind. The platform starts dockerd on a fresh container; this covers a resume.
    # 9>&-: dockerd outlives this script, and must not keep its lock.
    setsid nohup dockerd >/var/log/dockerd-cloud-setup.log 2>&1 < /dev/null 9>&- &
    for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
    docker info >/dev/null 2>&1 || { echo "cloud-setup: Docker did not start (log: /var/log/dockerd-cloud-setup.log)" >&2; exit 1; }
fi
if [[ "$(docker inspect -f '{{.State.Running}}' mcorg-db 2>/dev/null)" != "true" ]]; then
    step "starting the local postgres container"
    run "$WEBAPP_DIR/scripts/start-db.sh"
fi
for _ in $(seq 1 30); do psql -h localhost -U postgres -tAc 'select 1' >/dev/null 2>&1 && break; sleep 1; done

if [[ "$BACKGROUND" == true ]]; then
    echo running > "$SEAM_CLOUD_STATUS"
    # setsid + nohup, as for dockerd: the hook's process group does not take it down. It keeps
    # fd 9, and with it the lock, until it is done.
    phase_b_args=(--phase-b)
    [[ "$INGEST" == true ]] && phase_b_args+=(--ingest)
    [[ "$SEED" == true ]] && phase_b_args+=(--seed)
    # The one child that does keep fd 9: it holds the lock until the build is done.
    setsid nohup "$0" "${phase_b_args[@]}" >>"$LOG" 2>&1 < /dev/null &
    trap - EXIT
    step "ready in ${SECONDS}s; mvn install and migrations continue in the background (run.sh, test.sh and the ingest wait for them; progress: tail -f $LOG)"
    exit 0
fi

phase_b
step "ready in ${SECONDS}s (JAVA_HOME=$JDK_DIR, DB localhost:5432, $ON_DISK migrations)"
