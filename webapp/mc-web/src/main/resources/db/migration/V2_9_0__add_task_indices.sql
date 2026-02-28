-- Composite index for task stage filtering
CREATE INDEX idx_tasks_project_stage ON tasks(project_id, stage);

-- Index for task requirements completion status
CREATE INDEX idx_task_requirements_completion ON task_requirements(task_id, type, completed)
    WHERE type = 'ACTION';

CREATE INDEX idx_task_requirements_collection ON task_requirements(task_id, type, collected, required_amount)
    WHERE type = 'ITEM';