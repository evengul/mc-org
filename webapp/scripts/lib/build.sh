# Sourced by the dev scripts (run.sh, ingest-locally.sh, test.sh, migrate-locally.sh,
# cloud-setup.sh). Two things live here because every one of them needs them (MCO-658):
#
#   seam_install                 `mvn install -DskipTests`, skipped when it would change nothing
#   seam_wait_for_cloud_setup    in a cloud session, wait for the hook's background build
#
# Expects WEBAPP_DIR to be set to the webapp/ directory. Made absolute here, because callers set
# it relative to $0 and then cd, which would leave a relative path pointing nowhere.
WEBAPP_DIR="$(cd "$WEBAPP_DIR" && pwd)"

# The Maven repository installs go to: a worktree's own (MCO-471, via .mvn/maven.config), or
# ~/.m2/repository.
seam_maven_repo() {
    local configured=""
    if [[ -f "$WEBAPP_DIR/.mvn/maven.config" ]]; then
        configured="$(sed -n 's/^-Dmaven\.repo\.local=//p' "$WEBAPP_DIR/.mvn/maven.config" | tail -n1)"
    fi
    echo "${configured:-$HOME/.m2/repository}"
}

# What the installed jars are built from: every pom, and everything under a module's src/main.
# Directories are listed too, because deleting or renaming a file changes its directory's mtime
# and nothing else.
#
# Also this file (the install command lives here) and .mvn/ (maven.config, jvm.config): a change
# to how the build runs is a reason to run it.
_seam_changed_since() {
    local stamp="$1"
    find "$WEBAPP_DIR" -maxdepth 2 -name pom.xml -newer "$stamp" -print -quit | grep -q . && return 0
    find "$WEBAPP_DIR/scripts/lib/build.sh" "$WEBAPP_DIR/.mvn" -newer "$stamp" -print -quit 2>/dev/null | grep -q . && return 0
    find "$WEBAPP_DIR"/*/src/main -newer "$stamp" -print -quit 2>/dev/null | grep -q .
}

# Every module's jar is in the repository, and mc-web's classes are in target/: `exec:java -pl
# mc-web` runs from target/classes, which a bare `mvn clean` removes without touching a source.
_seam_outputs_present() {
    local dir="$1" pom module
    [[ -d "$WEBAPP_DIR/mc-web/target/classes" ]] || return 1
    for pom in "$WEBAPP_DIR"/*/pom.xml; do
        module="$(basename "$(dirname "$pom")")"
        [[ -d "$WEBAPP_DIR/$module/src/main" ]] || continue   # mc-bom: a pom, no jar
        compgen -G "$dir/$module/*/$module-*.jar" >/dev/null || return 1
    done
}

# `mvn install -DskipTests` for the whole reactor, unless nothing it is built from has changed
# since the last install from THIS checkout into THIS repository.
#
# Why install at all (MCO-285): a `-pl mc-web` run resolves the sibling modules from the Maven
# repository, so it must never see jars older than the source. The skip keeps that guarantee:
#   - the stamp's mtime is taken BEFORE the build, so an edit made during it still counts;
#   - the stamp names the checkout that wrote it, so another checkout sharing ~/.m2 (the main
#     checkout locally) invalidates it by installing;
#   - the stamp is only written when the install succeeded, and counts only while every module's
#     jar is installed and mc-web/target/classes exists.
# What it cannot see: a file put back with an OLD mtime (`cp -p`, `touch -d`). SEAM_FORCE_INSTALL=1
# installs regardless.
#
# Installs into one repository serialize on a lock, so the second of two concurrent callers waits
# and then finds the stamp fresh. Without flock (macOS) they run unlocked, as before.
#
# Trimmed for local use: no fat jar (the Dockerfile and CI build their own; nothing local reads
# it), and parallel artifact downloads, which only matter on a cold cache.
seam_install() {
    local repo dir stamp lock checkout started
    repo="$(seam_maven_repo)"
    dir="$repo/app/mcorg"
    stamp="$dir/.seam-install-stamp"
    lock="$dir/.seam-install.lock"
    checkout="$(cd "$WEBAPP_DIR" && pwd -P)"
    mkdir -p "$dir"

    if command -v flock >/dev/null 2>&1; then
        exec 8>"$lock"
        flock -x 8
    fi

    if [[ "${SEAM_FORCE_INSTALL:-}" != 1 && -f "$stamp" ]] \
        && [[ "$(cat "$stamp")" == "$checkout" ]] \
        && _seam_outputs_present "$dir" \
        && ! _seam_changed_since "$stamp"; then
        echo "Build: up to date since the last install (SEAM_FORCE_INSTALL=1 to force)."
        exec 8>&- 2>/dev/null || true
        return 0
    fi

    # Written now, not after the build: writing it afterwards would move its mtime past any edit
    # made while the build ran.
    started="$(mktemp "$dir/.seam-install-stamp.XXXXXX")"
    echo "$checkout" > "$started"
    echo "Building..."
    # 8>&- 9>&-: nothing the build starts may keep a lock alive after it (fd 9 is cloud-setup's).
    if (cd "$WEBAPP_DIR" && mvn install -q -B -DskipTests -Dassembly.skipAssembly=true -Dmaven.artifact.threads=16 8>&- 9>&-); then
        # mv keeps $started's mtime, which is the moment before the build began.
        mv -f "$started" "$stamp"
        exec 8>&- 2>/dev/null || true
    else
        local status=$?
        rm -f "$started" "$stamp"
        exec 8>&- 2>/dev/null || true
        return "$status"
    fi
}

# A cloud session's SessionStart hook returns once Docker and the DB are up, and builds and
# migrates in the background (cloud-setup.sh --background). Anything that needs the build waits
# here for it, and fails if the background run failed. A no-op outside a cloud session, and inside
# cloud-setup.sh itself (which holds the lock while it calls the other scripts).
SEAM_CLOUD_LOCK="${TMPDIR:-/tmp}/seam-cloud-setup.lock"
SEAM_CLOUD_STATUS="${TMPDIR:-/tmp}/seam-cloud-setup.status"
SEAM_CLOUD_LOG="${TMPDIR:-/tmp}/seam-cloud-setup.log"

seam_wait_for_cloud_setup() {
    [[ "${CLAUDE_CODE_REMOTE:-}" == true ]] || return 0
    [[ -z "${SEAM_CLOUD_SETUP_RUNNING:-}" ]] || return 0
    [[ -e "$SEAM_CLOUD_LOCK" ]] || return 0
    command -v flock >/dev/null 2>&1 || return 0

    if ! flock -n -s "$SEAM_CLOUD_LOCK" true; then
        echo "Waiting for the cloud setup's background build (progress: tail -f $SEAM_CLOUD_LOG)..."
        flock -s "$SEAM_CLOUD_LOCK" true
    fi
    # Only "ok" is success. "running" with the lock free means the build died without its EXIT
    # trap (killed), which is a failure too, not a finished build.
    local status
    status="$(cat "$SEAM_CLOUD_STATUS" 2>/dev/null || echo ok)"
    if [[ "$status" != ok ]]; then
        [[ "$status" == running ]] && status="stopped before it finished"
        echo "The cloud setup's background build $status (log: $SEAM_CLOUD_LOG)." >&2
        echo "Fix it and run webapp/scripts/cloud-setup.sh." >&2
        return 1
    fi
}
