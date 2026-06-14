-- Re-scope resource_gathering_plan_override from per-gathering to per-project.
-- The engine re-derives plans from graph + targets + overrides; overrides are
-- user-pinned item choices that are project-scoped, not gathering-scoped.

-- 1. Add project_id column (nullable first for backfill)
ALTER TABLE resource_gathering_plan_override
    ADD COLUMN project_id INTEGER REFERENCES projects(id) ON DELETE CASCADE;

-- 2. Backfill project_id from resource_gathering via resource_gathering_id
UPDATE resource_gathering_plan_override rgpo
SET project_id = rg.project_id
FROM resource_gathering rg
WHERE rg.id = rgpo.resource_gathering_id;

-- 3. Defensively dedupe: keep the most-recently-updated row per (project_id, item_id);
--    remove earlier duplicates before adding the unique constraint.
DELETE FROM resource_gathering_plan_override
WHERE id NOT IN (
    SELECT DISTINCT ON (project_id, item_id) id
    FROM resource_gathering_plan_override
    ORDER BY project_id, item_id, updated_at DESC, id DESC
);

-- 4. Drop old unique constraint, old index, and the resource_gathering_id column
ALTER TABLE resource_gathering_plan_override
    DROP CONSTRAINT unique_override_per_item;
DROP INDEX idx_rgpo_gathering_id;
ALTER TABLE resource_gathering_plan_override
    DROP COLUMN resource_gathering_id;

-- 5. Make project_id NOT NULL now that it is backfilled
ALTER TABLE resource_gathering_plan_override
    ALTER COLUMN project_id SET NOT NULL;

-- 6. Add new unique constraint and index
ALTER TABLE resource_gathering_plan_override
    ADD CONSTRAINT unique_override_per_project_item UNIQUE (project_id, item_id);
CREATE INDEX idx_rgpo_project_id ON resource_gathering_plan_override(project_id);

-- 7. Update comments
COMMENT ON TABLE resource_gathering_plan_override IS 'User-pinned planning choices per project item; plans are re-derived from these by the engine';
COMMENT ON COLUMN resource_gathering_plan_override.project_id IS 'Project that owns this override';
COMMENT ON COLUMN resource_gathering_plan_override.item_id IS 'Minecraft item or tag id the override applies to';
COMMENT ON COLUMN resource_gathering_plan_override.source_key IS 'Pinned SourceNode key (type id + filename) for the item';
COMMENT ON COLUMN resource_gathering_plan_override.tag_member IS 'Chosen member item id when item_id is a tag';
