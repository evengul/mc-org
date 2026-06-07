-- Drop the FK from the ingestion ledger to minecraft_version.
-- The ledger tracks ingestion *attempts* — including 'in_progress' and 'failed' rows for versions
-- that do not yet (or may never) exist in minecraft_version, since ingestion is what creates that
-- row. The FK forced the parent to exist before we could mark a run in progress, which is backwards.
-- version stays PRIMARY KEY (one ledger row per version). See MCO-167.
ALTER TABLE minecraft_version_ingestion
    DROP CONSTRAINT IF EXISTS minecraft_version_ingestion_version_fkey;
