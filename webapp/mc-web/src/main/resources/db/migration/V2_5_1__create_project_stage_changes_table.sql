CREATE TABLE project_stage_changes (
   id SERIAL PRIMARY KEY,
   project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
   stage VARCHAR(50) NOT NULL CHECK (stage IN ('IDEA', 'DESIGN', 'PLANNING', 'RESOURCE_GATHERING', 'BUILDING', 'TESTING', 'COMPLETED')),
   changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
   UNIQUE(project_id, stage)
);

-- Indexes for better performance
CREATE INDEX idx_project_stage_changes_project_id ON project_stage_changes(project_id);
CREATE INDEX idx_project_stage_changes_changed_at ON project_stage_changes(changed_at);