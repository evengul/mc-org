-- Create table for storing selected resource gathering paths
-- Phase 2: Resource Path Selection MVP

CREATE TABLE resource_gathering_plan (
    id SERIAL PRIMARY KEY,
    resource_gathering_id INTEGER NOT NULL REFERENCES resource_gathering(id) ON DELETE CASCADE,
    selected_path TEXT NOT NULL,  -- Encoded ProductionPath
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT unique_plan_per_gathering UNIQUE (resource_gathering_id)
);

CREATE INDEX idx_resource_gathering_plan_gathering_id
    ON resource_gathering_plan(resource_gathering_id);

COMMENT ON TABLE resource_gathering_plan IS 'Stores user-selected production paths for resource gathering items';
COMMENT ON COLUMN resource_gathering_plan.selected_path IS 'URL-encoded ProductionPath string (format: item>source~req1>source|req2>source)';

