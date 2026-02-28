CREATE TABLE project_dependencies (
    id SERIAL PRIMARY KEY,
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    depends_on_project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    depends_on_task_ids INTEGER[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, depends_on_project_id)
);

-- Indexes for better performance
CREATE INDEX idx_project_dependencies_project_id ON project_dependencies(project_id);
CREATE INDEX idx_project_dependencies_depends_on_project_id ON project_dependencies(depends_on_project_id);
CREATE INDEX idx_project_dependencies_task_ids ON project_dependencies USING GIN(depends_on_task_ids);

