#!/usr/bin/env bash
# Migrate the database that THIS checkout's local.env points at.
#
# Unlike migrate-locally.sh (which hardcodes the localhost Docker DB), this reads
# DB_URL / DB_USER / DB_PASSWORD from webapp/local.env — so in a git worktree it
# targets that worktree's isolated Neon branch (written by worktree-db.sh), and in
# the main checkout it targets whatever local.env already points at.
set -euo pipefail

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "Error: JAVA_HOME is not set"
    exit 1
fi

cd "$(dirname "$0")/.."   # -> webapp/

ENV_FILE="local.env"
if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: $ENV_FILE not found. In a worktree, run: bash scripts/worktree-db.sh"
    exit 1
fi

# Export DB_* so the flyway-maven-plugin picks them up from the environment.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_URL:?DB_URL missing from $ENV_FILE}"
: "${DB_USER:?DB_USER missing from $ENV_FILE}"
: "${DB_PASSWORD:?DB_PASSWORD missing from $ENV_FILE}"

# `-pl` resolves the sibling modules from the Maven repository rather than the
# reactor, and a worktree's repository holds no app.mcorg jars until something has
# been installed into it. worktree-db.sh does that at provisioning time (MCO-510),
# so this normally just works — but say so plainly if it hasn't, instead of letting
# Maven report six unresolvable artifacts.
if [[ -f .mvn/maven.config ]] && [[ -z "$(find .m2/repository/app/mcorg -name '*.jar' 2>/dev/null | head -1)" ]]; then
    echo "Error: this worktree's Maven repository has no app.mcorg jars yet, so"
    echo "       'flyway:migrate -pl mc-web' cannot resolve mc-domain and friends."
    echo "       Run 'mvn -DskipTests install' (or bash scripts/worktree-db.sh) first."
    exit 1
fi

# Redacted host echo so it's obvious which branch is being migrated.
echo "Migrating: ${DB_URL%%\?*}"
mvn flyway:migrate -pl mc-web
