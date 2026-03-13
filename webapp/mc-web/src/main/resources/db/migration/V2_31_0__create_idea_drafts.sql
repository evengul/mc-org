CREATE TABLE idea_drafts (
    id            SERIAL PRIMARY KEY,
    user_id       INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data          JSONB NOT NULL DEFAULT '{}',
    current_stage VARCHAR(50) NOT NULL DEFAULT 'BASIC_INFO',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_idea_drafts_user_id ON idea_drafts(user_id);
CREATE INDEX idx_idea_drafts_updated_at ON idea_drafts(updated_at DESC);
