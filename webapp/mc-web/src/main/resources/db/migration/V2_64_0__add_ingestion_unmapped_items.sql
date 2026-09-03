-- MCO-475: which of a version's item ids no `ItemGlyph` rule covers.
--
-- Seam draws its own item icons from a rule-based mapping over the item id, so a new Minecraft
-- version can ship items that match no rule — with no code change and no signal. The check itself
-- is free and stateless (run every id through the resolver), so nothing here stores a *computation*;
-- it stores the *answer*, because the process that computes it has nowhere to say it. The nightly
-- ingest runs on a short-lived Fly machine with ~30 minutes of log retention and no drain yet
-- (MCO-343), so a `logger.warn` there is written to a channel nobody reads that then deletes itself.
--
-- The ledger row is the right home: already per-version, already idempotent under FORCE_REINGEST.
-- The column is recomputed on every completed ingestion, so adding a glyph rule clears the gap on
-- the next nightly with no manual resolution step and no second table to keep in sync — and the
-- history comes for free (which version introduced a gap, and how long it stood).
--
-- Empty rather than NULL: '{}' means "checked, no gaps", which is the state every backfilled row is
-- in until its next re-ingest. A NULL/empty distinction would only be readable by something that
-- knows when the column was introduced.
ALTER TABLE minecraft_version_ingestion
    ADD COLUMN unmapped_items TEXT[] NOT NULL DEFAULT '{}';
