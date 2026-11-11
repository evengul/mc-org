#!/usr/bin/env bash
# Tests for scripts/lib/build.sh (MCO-658): when seam_install skips `mvn install`, and how
# seam_wait_for_cloud_setup waits. A fake `mvn` on PATH records each install and writes the jar
# a real one would, so this runs in a second with no JDK.
#
#   bash webapp/scripts/tests/build-lib.test.sh
set -uo pipefail

LIB="$(cd "$(dirname "$0")/.." && pwd)/lib/build.sh"
ROOT="$(mktemp -d)"
export ROOT   # the fake mvn below reads it
trap 'rm -rf "$ROOT"' EXIT
FAILURES=0

fail() { echo "FAIL: $*"; FAILURES=$((FAILURES + 1)); }
pass() { echo "ok:   $*"; }

# A fake checkout: two modules with a pom and a src/main each.
new_checkout() {
    local dir="$ROOT/$1"
    mkdir -p "$dir/mc-domain/src/main/kotlin" "$dir/mc-web/src/main/kotlin"
    echo '<project/>' > "$dir/pom.xml"
    echo '<project/>' > "$dir/mc-domain/pom.xml"
    echo '<project/>' > "$dir/mc-web/pom.xml"
    echo 'class A' > "$dir/mc-domain/src/main/kotlin/A.kt"
    echo 'class B' > "$dir/mc-web/src/main/kotlin/B.kt"
    mkdir -p "$dir/scripts/lib" && echo '# lib' > "$dir/scripts/lib/build.sh"
    echo "$dir"
}

mkdir -p "$ROOT/bin"
cat > "$ROOT/bin/mvn" <<'MVN'
#!/usr/bin/env bash
echo install >> "$FAKE_MVN_CALLS"
[[ "${FAKE_MVN_FAIL:-}" == 1 ]] && exit 1
for m in mc-domain mc-web; do
    mkdir -p "$HOME/.m2/repository/app/mcorg/$m/0.0.1" && touch "$HOME/.m2/repository/app/mcorg/$m/0.0.1/$m-0.0.1.jar"
done
mkdir -p "$PWD/mc-web/target/classes"
MVN
chmod +x "$ROOT/bin/mvn"
export PATH="$ROOT/bin:$PATH" HOME="$ROOT/home" FAKE_MVN_CALLS="$ROOT/calls"
mkdir -p "$HOME"

installs() { wc -l < "$FAKE_MVN_CALLS" 2>/dev/null | tr -d ' ' || echo 0; }

# Runs seam_install for a checkout in a subshell; prints nothing, returns its status.
install_in() { ( WEBAPP_DIR="$1"; . "$LIB"; seam_install ) >/dev/null 2>&1; }

expect_installs() {
    local want="$1" what="$2" got
    got="$(installs)"
    if [[ "$got" == "$want" ]]; then pass "$what"; else fail "$what (installs: want $want, got $got)"; fi
}

A="$(new_checkout a)"
: > "$FAKE_MVN_CALLS"

install_in "$A";                 expect_installs 1 "first run installs"
install_in "$A";                 expect_installs 1 "a second run with nothing changed skips"

sleep 0.05; echo 'class A2' >> "$A/mc-domain/src/main/kotlin/A.kt"
install_in "$A";                 expect_installs 2 "an edited source file installs"

sleep 0.05; rm "$A/mc-web/src/main/kotlin/B.kt"
install_in "$A";                 expect_installs 3 "a deleted source file installs (directory mtime)"

sleep 0.05; touch "$A/mc-web/pom.xml"
install_in "$A";                 expect_installs 4 "a changed pom installs"

sleep 0.05; mkdir -p "$A/mc-web/src/test/kotlin"; echo 'class T' > "$A/mc-web/src/test/kotlin/T.kt"
install_in "$A";                 expect_installs 4 "a test-only change does not install"

SEAM_FORCE_INSTALL=1 install_in "$A"; expect_installs 5 "SEAM_FORCE_INSTALL=1 installs"

B="$(new_checkout b)"
install_in "$B";                 expect_installs 6 "another checkout sharing the repository installs"
install_in "$A";                 expect_installs 7 "...and then the first checkout installs again"

rm -rf "$HOME/.m2/repository/app/mcorg/mc-domain"
install_in "$A";                 expect_installs 8 "a sibling module's missing jar installs"

sleep 0.05; echo 'class A3' >> "$A/mc-domain/src/main/kotlin/A.kt"
FAKE_MVN_FAIL=1 install_in "$A"; rc=$?
[[ $rc != 0 ]] && pass "a failed install fails the caller" || fail "a failed install returned 0"
install_in "$A";                 expect_installs 10 "after a failed install the next run installs"

rm -rf "$A/mc-web/target/classes"
install_in "$A";                 expect_installs 11 "mc-web/target/classes gone (mvn clean) installs"

sleep 0.05; touch "$A/scripts/lib/build.sh"
install_in "$A";                 expect_installs 12 "a change to the install command (lib/build.sh) installs"

# An edit made while the build runs counts: the stamp is taken before the build.
cat > "$ROOT/bin/mvn" <<'MVN'
#!/usr/bin/env bash
echo install >> "$FAKE_MVN_CALLS"
sleep 0.05; echo 'class During' >> "$EDIT_DURING"
for m in mc-domain mc-web; do
    mkdir -p "$HOME/.m2/repository/app/mcorg/$m/0.0.1" && touch "$HOME/.m2/repository/app/mcorg/$m/0.0.1/$m-0.0.1.jar"
done
mkdir -p "$PWD/mc-web/target/classes"
MVN
sleep 0.05; touch "$A/pom.xml"
EDIT_DURING="$A/mc-domain/src/main/kotlin/A.kt" install_in "$A"
EDIT_DURING="$ROOT/elsewhere.txt" install_in "$A"
expect_installs 14 "an edit made during the install makes the next run install"

# Two concurrent callers: the second waits for the first, then finds the stamp fresh.
cat > "$ROOT/bin/mvn" <<'MVN'
#!/usr/bin/env bash
echo install >> "$FAKE_MVN_CALLS"
touch "$ROOT/installing"; sleep 0.5
for m in mc-domain mc-web; do
    mkdir -p "$HOME/.m2/repository/app/mcorg/$m/0.0.1" && touch "$HOME/.m2/repository/app/mcorg/$m/0.0.1/$m-0.0.1.jar"
done
mkdir -p "$PWD/mc-web/target/classes"
MVN
sleep 0.05; touch "$A/pom.xml"
rm -f "$ROOT/installing"
install_in "$A" &
# The first holds the lock and is building. Bounded, so a broken test fails rather than hangs.
for _ in $(seq 1 250); do [[ -e "$ROOT/installing" ]] && break; sleep 0.02; done
install_in "$A"; wait
expect_installs 15 "a concurrent second caller waits and skips"

# --- seam_wait_for_cloud_setup -----------------------------------------------------------------
export TMPDIR="$ROOT/tmp"; mkdir -p "$TMPDIR"
wait_cloud() { ( WEBAPP_DIR="$A"; . "$LIB"; seam_wait_for_cloud_setup ) >/dev/null 2>&1; }

CLAUDE_CODE_REMOTE= wait_cloud && pass "outside a cloud session it returns at once" || fail "outside a cloud session it failed"

export CLAUDE_CODE_REMOTE=true
touch "$TMPDIR/seam-cloud-setup.lock"; echo running > "$TMPDIR/seam-cloud-setup.status"
( exec 9>"$TMPDIR/seam-cloud-setup.lock"; flock -x 9; touch "$ROOT/held1"; sleep 0.6; echo ok > "$TMPDIR/seam-cloud-setup.status" ) &
for _ in $(seq 1 250); do [[ -e "$ROOT/held1" ]] && break; sleep 0.02; done; start=$(date +%s%N); wait_cloud; rc=$?; waited=$(( ($(date +%s%N) - start) / 1000000 )); wait
[[ $rc == 0 && $waited -ge 300 ]] && pass "it waits for a running background build (${waited} ms)" \
    || fail "it did not wait (rc $rc, ${waited} ms)"

echo "failed at: mvn install" > "$TMPDIR/seam-cloud-setup.status"
wait_cloud && fail "a failed background build was not reported" || pass "a failed background build fails the caller"

echo running > "$TMPDIR/seam-cloud-setup.status"
wait_cloud && fail "a killed background build (status left 'running') read as done" \
    || pass "a killed background build (status left 'running', lock free) fails the caller"
echo ok > "$TMPDIR/seam-cloud-setup.status"

( exec 9>"$TMPDIR/seam-cloud-setup.lock"; flock -x 9; touch "$ROOT/held2"; sleep 0.6 ) &
for _ in $(seq 1 250); do [[ -e "$ROOT/held2" ]] && break; sleep 0.02; done; start=$(date +%s%N); SEAM_CLOUD_SETUP_RUNNING=1 wait_cloud
waited=$(( ($(date +%s%N) - start) / 1000000 )); wait
[[ $waited -lt 300 ]] && pass "cloud-setup's own children do not wait for its lock" || fail "a child waited (${waited} ms): deadlock"

echo
if [[ $FAILURES == 0 ]]; then echo "all passed"; else echo "$FAILURES failed"; exit 1; fi
