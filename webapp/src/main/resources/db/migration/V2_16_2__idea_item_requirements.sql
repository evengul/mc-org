CREATE TABLE idea_item_requirements (
    idea_id INTEGER NOT NULL,
    item_id VARCHAR NOT NULL,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (idea_id, item_id),
    FOREIGN KEY (idea_id) REFERENCES ideas(id) ON DELETE CASCADE
);

CREATE INDEX idx_idea_item_requirements_idea_id ON idea_item_requirements(idea_id);