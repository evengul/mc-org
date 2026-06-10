-- The persisted plan tree is replaced by per-item plan overrides: the engine
-- re-derives the full GatheringPlan from the graph + targets + overrides on
-- read, so only the user's explicit choices need to be stored.
--
-- The plan tables held a single orphaned row (feature was unwired by the
-- frontend refactor; verified against production data before this migration).

DROP TABLE resource_gathering_plan_node;
DROP TABLE resource_gathering_plan;

CREATE TABLE resource_gathering_plan_override (
    id SERIAL PRIMARY KEY,
    resource_gathering_id INTEGER NOT NULL REFERENCES resource_gathering(id) ON DELETE CASCADE,
    item_id VARCHAR NOT NULL,
    source_key VARCHAR,
    tag_member VARCHAR,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT unique_override_per_item UNIQUE (resource_gathering_id, item_id),
    CONSTRAINT override_has_choice CHECK (source_key IS NOT NULL OR tag_member IS NOT NULL)
);

CREATE INDEX idx_rgpo_gathering_id ON resource_gathering_plan_override(resource_gathering_id);

COMMENT ON TABLE resource_gathering_plan_override IS 'User-pinned planning choices per resource gathering item; plans are re-derived from these by the engine';
COMMENT ON COLUMN resource_gathering_plan_override.item_id IS 'Minecraft item or tag id the override applies to';
COMMENT ON COLUMN resource_gathering_plan_override.source_key IS 'Pinned SourceNode key (type id + filename) for the item';
COMMENT ON COLUMN resource_gathering_plan_override.tag_member IS 'Chosen member item id when item_id is a tag';
