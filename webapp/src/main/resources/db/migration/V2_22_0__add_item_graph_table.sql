CREATE TABLE item_graph (
    id SERIAL PRIMARY KEY,
    minecraft_version VARCHAR(20) NOT NULL,
    graph_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_item_graph_minecraft_version ON item_graph (minecraft_version);