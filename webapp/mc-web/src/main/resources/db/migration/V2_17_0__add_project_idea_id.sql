ALTER TABLE projects ADD COLUMN project_idea_id INT;

ALTER TABLE projects ADD CONSTRAINT fk_project_idea
    FOREIGN KEY (project_idea_id)
    REFERENCES ideas(id)
    ON DELETE SET NULL;