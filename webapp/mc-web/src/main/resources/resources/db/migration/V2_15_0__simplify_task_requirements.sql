DELETE FROM tasks WHERE id IN (
    SELECT DISTINCT task_id FROM task_requirements
);

DROP table task_requirements;

ALTER TABLE tasks ADD COLUMN requirement_type VARCHAR(20) NOT NULL CHECK (requirement_type IN ('ITEM', 'ACTION')) DEFAULT 'ACTION';
ALTER TABLE tasks ADD COLUMN requirement_item_required_amount INTEGER NULL;
ALTER TABLE tasks ADD COLUMN requirement_item_collected INTEGER NULL;
ALTER TABLE tasks ADD COLUMN requirement_action_completed BOOLEAN NULL;