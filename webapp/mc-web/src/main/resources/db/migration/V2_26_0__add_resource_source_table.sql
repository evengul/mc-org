CREATE TABLE resource_source (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_from_filename VARCHAR(255) NOT NULL
);

CREATE INDEX idx_resource_source_source_type
    ON resource_source(version, source_type);

CREATE TABLE resource_source_produced_item (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    resource_source_id INTEGER NOT NULL,
    item VARCHAR(128) NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CHECK ( count > 0 ),

    FOREIGN KEY (resource_source_id) REFERENCES resource_source(id) ON DELETE CASCADE,
    FOREIGN KEY (version, item) REFERENCES minecraft_items(version, item_id) ON DELETE CASCADE
);

CREATE TABLE resource_source_produced_tag (
   id SERIAL PRIMARY KEY,
   version VARCHAR(50) NOT NULL,
   resource_source_id INTEGER NOT NULL,
   tag VARCHAR(128) NOT NULL,
   count INTEGER NOT NULL DEFAULT 1,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

   CHECK ( count > 0 ),

   FOREIGN KEY (resource_source_id) REFERENCES resource_source(id) ON DELETE CASCADE,
   FOREIGN KEY (version, tag) REFERENCES minecraft_tag(version, tag) ON DELETE CASCADE
);

CREATE TABLE resource_source_consumed_item (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    resource_source_id INTEGER NOT NULL,
    item VARCHAR(128) NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CHECK ( count > 0 ),

    FOREIGN KEY (resource_source_id) REFERENCES resource_source(id) ON DELETE CASCADE,
    FOREIGN KEY (version, item) REFERENCES minecraft_items(version, item_id) ON DELETE CASCADE
);

CREATE TABLE resource_source_consumed_tag (
    id SERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    resource_source_id INTEGER NOT NULL,
    tag VARCHAR(128) NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CHECK ( count > 0 ),

    FOREIGN KEY (resource_source_id) REFERENCES resource_source(id) ON DELETE CASCADE,
    FOREIGN KEY (version, tag) REFERENCES minecraft_tag(version, tag) ON DELETE CASCADE
);

-- Query: "What resource sources produce/consume this item/tag?"
CREATE INDEX idx_resource_source_produced_item_item
    ON resource_source_produced_item(version, item);

CREATE INDEX idx_resource_source_produced_tag_tag
    ON resource_source_produced_tag(version, tag);

CREATE INDEX idx_resource_source_consumed_item_item
    ON resource_source_consumed_item(version, item);

CREATE INDEX idx_resource_source_consumed_tag_tag
    ON resource_source_consumed_tag(version, tag);