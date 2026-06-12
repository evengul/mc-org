-- Project lifecycle state — a separate axis from stage (progress within the project).
-- Initial values are derived from the existing stage.
ALTER TABLE projects ADD COLUMN state VARCHAR(50);

UPDATE projects SET state = CASE
    WHEN stage IN ('IDEA', 'DESIGN', 'PLANNING') THEN 'PENDING'
    WHEN stage IN ('RESOURCE_GATHERING', 'BUILDING', 'TESTING') THEN 'ACTIVE'
    WHEN stage = 'COMPLETED' THEN 'DONE'
END;

ALTER TABLE projects ALTER COLUMN state SET NOT NULL;
ALTER TABLE projects ALTER COLUMN state SET DEFAULT 'PENDING';
ALTER TABLE projects ADD CONSTRAINT chk_projects_state
    CHECK (state IN ('PENDING', 'ACTIVE', 'PAUSED', 'DONE', 'CANCELLED', 'ARCHIVED'));

CREATE INDEX idx_projects_state ON projects(state);

-- The V2 projects table never had completed_at (V1_15 added it to the old singular
-- `project` table). Backfill from updated_at for already-completed projects.
ALTER TABLE projects ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

UPDATE projects SET completed_at = updated_at WHERE stage = 'COMPLETED';
