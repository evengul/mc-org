CREATE TABLE minecraft_version (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE
);

CREATE INDEX idx_minecraft_version ON minecraft_version(version);

INSERT INTO minecraft_version (version) (SELECT DISTINCT version FROM minecraft_items);

ALTER TABLE minecraft_items ADD FOREIGN KEY (version) REFERENCES minecraft_version(version);