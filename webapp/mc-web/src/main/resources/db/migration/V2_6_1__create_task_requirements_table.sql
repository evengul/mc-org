CREATE TABLE task_requirements (
    id SERIAL PRIMARY KEY,
    task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('ITEM', 'ACTION')),

    -- Fields for ItemRequirement
    item VARCHAR(255) NULL,
    required_amount INTEGER NULL,
    collected INTEGER NULL,

    -- Fields for ActionRequirement
    action VARCHAR(255) NULL,
    completed BOOLEAN NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints to ensure proper data based on type
    CONSTRAINT chk_item_requirement
        CHECK (type != 'ITEM' OR (item IS NOT NULL AND required_amount IS NOT NULL AND collected IS NOT NULL)),
    CONSTRAINT chk_action_requirement
        CHECK (type != 'ACTION' OR (action IS NOT NULL AND completed IS NOT NULL))
);

-- Indexes for better performance
CREATE INDEX idx_task_requirements_task_id ON task_requirements(task_id);
CREATE INDEX idx_task_requirements_type ON task_requirements(type);
CREATE INDEX idx_task_requirements_item ON task_requirements(item) WHERE type = 'ITEM';
CREATE INDEX idx_task_requirements_completed ON task_requirements(completed) WHERE type = 'ACTION';

