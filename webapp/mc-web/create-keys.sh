#!/bin/bash
# Generate (once per machine) the RSA pair that signs local JWTs, and copy it into
# this checkout's resources/keys/ — where jwt.kt's readKey() fallback expects it.
#
# One pair per MACHINE, not per checkout (MCO-508). The pair is gitignored, so every
# worktree used to generate its own; the auth cookie minted in one worktree was then
# unverifiable in the next, and you signed in again on every switch. Cookies are not
# port-scoped on localhost, so a shared key is all it takes for a session to carry
# across worktrees despite their different PORTs.
#
# Every caller comes through here — the EnterWorktree hook
# (.claude/hooks/worktree-create-keys.sh), webapp/scripts/test.sh, and CI
# (.github/workflows/dev.yml) — so they all inherit this with no change of their own.
# On a CI runner the key home does not exist and nothing has been generated yet, so
# the effect there is exactly the old behaviour: generate a throwaway pair.
#
# These keys are for LOCAL and CI only. Production and the preview apps take
# RSA_PRIVATE_KEY / RSA_PUBLIC_KEY from Fly secrets, which jwt.kt prefers over these
# files; see documentation/configuration.md.
set -euo pipefail

RESOURCES="$(cd "$(dirname "$0")" && pwd)/src/main/resources/keys"

# Where the shared pair lives. SEAM_KEY_HOME overrides it (the test uses that to get a
# scratch home). With no HOME to anchor the default, fall back to a checkout-local
# pair rather than guessing at a path — that is the pre-MCO-508 behaviour.
if [ -n "${SEAM_KEY_HOME:-}" ]; then
  KEY_HOME="$SEAM_KEY_HOME"
elif [ -n "${HOME:-}" ]; then
  KEY_HOME="${XDG_CONFIG_HOME:-$HOME/.config}/seam/keys"
else
  KEY_HOME="$RESOURCES"
fi

mkdir -p "$KEY_HOME"
chmod 700 "$KEY_HOME"

if [ ! -f "$KEY_HOME/private_key.pem" ] || [ ! -f "$KEY_HOME/public_key.pem" ]; then
  if [ "$KEY_HOME" != "$RESOURCES" ] \
     && [ -f "$RESOURCES/private_key.pem" ] && [ -f "$RESOURCES/public_key.pem" ]; then
    # First run after MCO-508 in a checkout that already had a pair: adopt it as the
    # machine pair rather than generating. Keeps whatever session is currently signed
    # in valid, so the migration costs nobody a sign-in.
    cp -p "$RESOURCES/private_key.pem" "$RESOURCES/public_key.pem" "$KEY_HOME/"
    echo "create-keys: adopted this checkout's existing pair as the machine pair in $KEY_HOME"
  else
    openssl genpkey -algorithm RSA -out "$KEY_HOME/private_key.pem" -pkeyopt rsa_keygen_bits:2048
    openssl rsa -pubout -in "$KEY_HOME/private_key.pem" -out "$KEY_HOME/public_key.pem"
    echo "create-keys: generated a new machine pair in $KEY_HOME"
  fi
fi

chmod 600 "$KEY_HOME/private_key.pem"

if [ "$KEY_HOME" != "$RESOURCES" ]; then
  mkdir -p "$RESOURCES"
  cp -p "$KEY_HOME/private_key.pem" "$KEY_HOME/public_key.pem" "$RESOURCES/"
fi
