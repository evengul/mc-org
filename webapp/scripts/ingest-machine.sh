#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Server-files ingestion as a Fly scheduled machine (MCO-171).
#
# Ingestion no longer runs inside the web process. Instead it runs as its own
# short-lived Fly machine that starts, runs the MCO-170 CLI entry point
# (app.mcorg.cli.IngestServerFilesKt) once, and exits. We reuse the EXACT image
# the web app is already running — the fat jar bundles the CLI class — and just
# override the Docker ENTRYPOINT to launch the ingestion main instead of Netty.
#
# Two modes:
#   --once       Run a single immediate ingestion and exit (smoke test against
#                prod). The machine is destroyed afterwards (--rm).
#   (default)    Create the recurring DAILY scheduled machine. Daily is safe and
#                free: MCO-168's SHA check makes an unchanged manifest a no-op,
#                and MCO-169's advisory lock makes overlapping runs no-ops.
#
# Prereqs: `fly auth login`, access to the `mcorg` app. Run from anywhere.
#
# Env/secrets: a machine created by `fly machine run` does NOT inherit fly.toml's
# [env] block, but it DOES inherit app-level secrets (DB_PASSWORD, RSA keys,
# Microsoft secret). So we pass the non-secret config explicitly below; the DB
# password comes from the app secret automatically. Ingestion only actually
# needs DB_* + a network path to Mojang — the other config is set for parity and
# to silence AppConfig's non-fatal "missing config" logging.
# ---------------------------------------------------------------------------

APP="mcorg"
REGION="arn"
# Match the web container's heap ceiling; the original ENTRYPOINT's -Xmx is
# dropped when we override it, so set it here. Ingestion parses server jars.
ENTRYPOINT_CMD="java -Xmx768m -cp /app/mcorg.jar app.mcorg.cli.IngestServerFilesKt"

# Non-secret env, mirrored from fly.toml's [env]. DB_PASSWORD is an app secret
# and is injected automatically — do NOT put it here.
ENV_ARGS=(
  --env "DB_URL=jdbc:postgresql://ep-icy-wood-a2vogqwy-pooler.eu-central-1.aws.neon.tech/mcorg?sslmode=require"
  --env "DB_USER=mcorg_owner"
  --env "ENV=PRODUCTION"
  --env "SKIP_MICROSOFT_SIGN_IN=true"
)

usage() {
  echo "Usage: $0 [--once]"
  echo ""
  echo "  --once   Run one immediate ingestion against prod and exit (smoke test)."
  echo "           Default (no flag) creates the recurring daily scheduled machine."
  exit 1
}

MODE="schedule"
case "${1:-}" in
  --once) MODE="once" ;;
  "")     MODE="schedule" ;;
  -h|--help) usage ;;
  *) echo "Unknown option: $1"; usage ;;
esac

# Resolve the exact image the app is currently running so the CLI runs identical
# code to the deployed web process. Honors a manual `IMAGE=<ref>` override; else
# pins the deployed image by digest from `fly image show --json`, which returns
# an ARRAY of {Registry, Repository, Tag, Digest} (e.g. the image lives on GHCR:
# ghcr.io/evengul/mc-org@sha256:...). Pinning by digest is immutable, unlike Tag.
if [[ -n "${IMAGE:-}" ]]; then
  echo "Using image from \$IMAGE override: $IMAGE"
else
  echo "Resolving current image for app '$APP'..."
  IMAGE="$(fly image show -a "$APP" --json 2>/dev/null \
    | jq -r 'first | .Registry + "/" + .Repository + "@" + .Digest')"
  if [[ -z "$IMAGE" || "$IMAGE" == *null* ]]; then
    echo "Could not auto-resolve the image. Run 'fly image show -a $APP', then re-run with:"
    echo "  IMAGE=<registry>/<repository>@<digest> $0 ${1:-}"
    exit 1
  fi
  echo "Using image: $IMAGE"
fi

if [[ "$MODE" == "once" ]]; then
  echo "Running a one-off ingestion (immediate, then destroyed)..."
  exec fly machine run "$IMAGE" \
    --app "$APP" \
    --region "$REGION" \
    --restart no \
    --rm \
    --vm-memory 1024 \
    --entrypoint "$ENTRYPOINT_CMD" \
    "${ENV_ARGS[@]}"
else
  echo "Creating the DAILY scheduled ingestion machine..."
  exec fly machine run "$IMAGE" \
    --app "$APP" \
    --region "$REGION" \
    --schedule daily \
    --restart no \
    --vm-memory 1024 \
    --entrypoint "$ENTRYPOINT_CMD" \
    "${ENV_ARGS[@]}"
fi
