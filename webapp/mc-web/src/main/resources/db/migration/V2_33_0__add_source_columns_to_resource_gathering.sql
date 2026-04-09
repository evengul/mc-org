ALTER TABLE resource_gathering
    ADD COLUMN source_type VARCHAR(20),
    ADD COLUMN solved_by_project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL;

CREATE INDEX idx_rg_solved_by_project ON resource_gathering(solved_by_project_id);
