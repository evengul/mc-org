#!/bin/bash
# Refresh the item-id snapshot that ItemGlyphTest pins the glyph rules against.
#
# Reads the newest ingested Minecraft version from whichever database local.env points at (the
# worktree's Neon branch, or the localhost Docker DB in the main checkout) and writes it to test
# resources. Run this after a new Minecraft version is ingested: the test will then fail naming any
# items the glyph rules do not cover, which is the point.
set -euo pipefail

cd "$(dirname "$0")/.."   # -> webapp/

set -a; . local.env; set +a
URL="$(printf '%s' "$DB_URL" | sed 's|^jdbc:||')&user=$DB_USER&password=$DB_PASSWORD"
DEST=mc-web/src/test/resources/minecraft
mkdir -p "$DEST"

# Version strings sort as integer arrays, so 1.21.10 lands after 1.21.9 rather than before it.
VERSION=$(psql "$URL" -qAt -c \
    "select version from minecraft_items group by version order by string_to_array(version,'.')::int[] desc limit 1")
echo "newest ingested version: $VERSION"

psql "$URL" -qAt -c \
    "select item_id from minecraft_items where version='$VERSION' order by item_id" > "$DEST/item-ids.txt"
echo "$VERSION" > "$DEST/item-ids.version"
echo "wrote $(wc -l < "$DEST/item-ids.txt") item ids to $DEST/item-ids.txt"
