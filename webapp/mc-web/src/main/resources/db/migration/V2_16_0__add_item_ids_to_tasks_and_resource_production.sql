-- Delete existing data to ensure clean migration
DELETE FROM project_productions;
DELETE FROM tasks;

-- Add item_id to tasks table
ALTER TABLE tasks
ADD COLUMN item_id INTEGER;

-- Add item_id to project_productions table
ALTER TABLE project_productions
ADD COLUMN item_id INTEGER NOT NULL;

-- Add check constraint to ensure item_id is non-null when requirement_type is 'ITEM'
ALTER TABLE tasks
ADD CONSTRAINT chk_tasks_item_id_requirement_type
CHECK (
    (requirement_type = 'ITEM' AND item_id IS NOT NULL) OR
    (requirement_type = 'ACTION' AND item_id IS NULL)
);

