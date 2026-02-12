ALTER TABLE minecraft_items ADD CONSTRAINT unique_item_per_version UNIQUE (version, item_id);

CREATE TABLE minecraft_tag_item (
    version VARCHAR (50) NOT NULL,
    tag VARCHAR(128) NOT NULL,
    item VARCHAR(128) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    PRIMARY KEY (version, tag, item),
    FOREIGN KEY (version, tag) REFERENCES minecraft_tag(minecraft_version, tag) ON DELETE CASCADE,
    FOREIGN KEY (version, item) REFERENCES minecraft_items(version, item_id) ON DELETE CASCADE
);