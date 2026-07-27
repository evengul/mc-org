-- Provenance for resource progress: which client last set the collected value (MCO-284).
--
-- The mod's snapshot is authoritative while the mod is running; the web app's manual counters are
-- the fallback when it isn't. Last-write-wins needs both sides to be able to tell which wrote the
-- value they're looking at, and the table only recorded WHAT was collected, never WHO set it.
--
-- Named progress_source, not source: resource_gathering already has source_type, which is the
-- item's acquisition type from the graph (MINED, CRAFTED, ...) and entirely unrelated.

ALTER TABLE resource_gathering_progress
    ADD COLUMN progress_source VARCHAR NOT NULL DEFAULT 'manual';

-- Every row written before this migration came from the web app, so the DEFAULT is also the
-- correct backfill; no UPDATE needed.

ALTER TABLE resource_gathering_progress
    ADD CONSTRAINT chk_rgp_progress_source CHECK (progress_source IN ('manual', 'mod'));

COMMENT ON COLUMN resource_gathering_progress.progress_source IS
    'Which client last set collected: manual (web app) or mod (Seam Companion sync)';
