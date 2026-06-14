-- Per-node progress table: tracks how many of each item the user has collected
-- for a project, decoupled from the resource_gathering rows.
-- This is the foundation for per-node progress in the gathering planner (MCO-221).

CREATE TABLE resource_gathering_progress (
    id         SERIAL PRIMARY KEY,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    item_id    VARCHAR NOT NULL,
    collected  INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_progress_per_project_item UNIQUE (project_id, item_id)
);

CREATE INDEX idx_rgp_project_id ON resource_gathering_progress(project_id);

COMMENT ON TABLE resource_gathering_progress IS 'Per-item collected progress for a project; keyed by (project_id, item_id)';
COMMENT ON COLUMN resource_gathering_progress.project_id IS 'Project that owns this progress entry';
COMMENT ON COLUMN resource_gathering_progress.item_id IS 'Minecraft item id this progress tracks';
COMMENT ON COLUMN resource_gathering_progress.collected IS 'Number of this item collected so far';

-- Backfill from resource_gathering.collected.
-- If duplicate (project_id, item_id) rows exist in resource_gathering
-- (which should not happen in practice given the unique item_id constraint),
-- SUM the collected values to avoid losing data.
INSERT INTO resource_gathering_progress (project_id, item_id, collected)
SELECT project_id, item_id, SUM(collected)
FROM resource_gathering
WHERE collected > 0
GROUP BY project_id, item_id
ON CONFLICT (project_id, item_id) DO UPDATE
    SET collected = EXCLUDED.collected;

-- Drop collected column from resource_gathering; progress is now in the progress table
ALTER TABLE resource_gathering DROP COLUMN collected;
