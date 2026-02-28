CREATE TABLE item_task (
    id SERIAL PRIMARY KEY,
    project_id INTEGER NOT NULL,
    item_id VARCHAR NOT NULL,
    name VARCHAR NOT NULL,
    required INTEGER NOT NULL,
    collected INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE action_task (
    id SERIAL PRIMARY KEY,
    project_id INTEGER NOT NULL,
    name VARCHAR NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX
    idx_item_task_project_id ON item_task(project_id);

CREATE INDEX
    idx_action_task_project_id ON action_task(project_id);

INSERT INTO item_task (project_id, item_id, name, required, collected, created_at, updated_at)
SELECT
    t.project_id,
    coalesce(t.item_id, 'unknown') AS item_id,
    t.name,
    COALESCE(t.requirement_item_required_amount, 0) AS required,
    COALESCE(t.requirement_item_collected, 0) AS collected,
    t.created_at,
    t.updated_at
FROM
    tasks t
where t.requirement_type = 'ITEM';

INSERT INTO action_task (project_id, name, completed, created_at, updated_at)
SELECT
    t.project_id,
    t.name,
    COALESCE(t.requirement_action_completed, FALSE) AS completed,
    t.created_at,
    t.updated_at
FROM
    tasks t
where t.requirement_type = 'ACTION';

