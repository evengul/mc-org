-- Normalize resource_gathering_plan: replace encoded TEXT column with proper relational table
-- Data loss is acceptable — feature is in development

-- Clear existing plans (they used the old encoded format)
TRUNCATE resource_gathering_plan;

-- Drop the old encoded path column
ALTER TABLE resource_gathering_plan DROP COLUMN selected_path;

-- Create normalized node table using adjacency list pattern
CREATE TABLE resource_gathering_plan_node (
    id SERIAL PRIMARY KEY,
    plan_id INTEGER NOT NULL REFERENCES resource_gathering_plan(id) ON DELETE CASCADE,
    parent_node_id INTEGER REFERENCES resource_gathering_plan_node(id) ON DELETE CASCADE,
    item_id VARCHAR NOT NULL,
    source VARCHAR,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_rgpn_plan_id ON resource_gathering_plan_node(plan_id);
CREATE INDEX idx_rgpn_parent_node_id ON resource_gathering_plan_node(parent_node_id);
