ALTER TABLE ideas ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE idea_drafts ADD COLUMN source_idea_id INT REFERENCES ideas(id) ON DELETE SET NULL;
CREATE INDEX idx_ideas_is_active ON ideas(is_active) WHERE NOT is_active;
