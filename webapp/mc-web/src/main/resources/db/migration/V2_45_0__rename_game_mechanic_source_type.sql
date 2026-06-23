-- Rename the synthetic source type id `mcorg:game_mechanic` -> `mcorg:in_world_transform`
-- (Kotlin symbol MechanicTypes.GAME_MECHANIC -> IN_WORLD_TRANSFORM). The old name was vague;
-- "in-world transform" describes what it models (place concrete powder by water to harden it).
--
-- The id is persisted in three places. Synthetic sources are new in this unreleased batch, so in
-- production these rewrites match zero rows (the pending extraction_version 0->1 re-ingest creates
-- the rows with the new id directly). The migration keeps any dev/preview env that already ingested
-- under the old id consistent, without forcing another re-ingest.

-- 1. resource_source.source_type (VARCHAR(50)) — the extracted source rows.
UPDATE resource_source
SET source_type = 'mcorg:in_world_transform'
WHERE source_type = 'mcorg:game_mechanic';

-- 2. resource_gathering.source_type (VARCHAR(20)) — user-chosen source per gathered item.
--    The new id is 24 chars and overflows VARCHAR(20); widen to match resource_source's 50 first.
ALTER TABLE resource_gathering
    ALTER COLUMN source_type TYPE VARCHAR(50);
UPDATE resource_gathering
SET source_type = 'mcorg:in_world_transform'
WHERE source_type = 'mcorg:game_mechanic';

-- 3. resource_gathering_plan_override.source_key (VARCHAR) — pinned `<type id>:<filename>` composite.
--    Rewrite just the type-id prefix, preserving the filename suffix.
UPDATE resource_gathering_plan_override
SET source_key = 'mcorg:in_world_transform' || substring(source_key from length('mcorg:game_mechanic') + 1)
WHERE source_key LIKE 'mcorg:game_mechanic:%';
