CREATE TABLE minecraft_items (
    id SERIAL PRIMARY KEY,
    version VARCHAR(20) NOT NULL,
    item_id VARCHAR(100) NOT NULL,
    item_name VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX ux_minecraft_items_version_item_id
    ON minecraft_items (version, item_id);

CREATE INDEX idx_minecraft_items_version
    ON minecraft_items (version);