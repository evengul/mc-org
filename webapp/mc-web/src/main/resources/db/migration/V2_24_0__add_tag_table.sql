CREATE TABLE minecraft_tag (
    id SERIAL PRIMARY KEY,
    minecraft_version VARCHAR(50) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT unique_tag_per_version UNIQUE (minecraft_version, tag),
    CONSTRAINT tag_starts_with_hash CHECK (tag LIKE '#%')
);

CREATE INDEX idx_minecraft_tag_tag
    ON minecraft_tag(tag);