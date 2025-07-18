CREATE TABLE projects (
    id SERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('BUILDING', 'REDSTONE', 'MINING', 'FARMING', 'EXPLORATION', 'DECORATION', 'TECHNICAL')),
    stage VARCHAR(50) NOT NULL CHECK (stage IN ('IDEA', 'DESIGN', 'PLANNING', 'RESOURCE_GATHERING', 'BUILDING', 'TESTING', 'COMPLETED')),
    location_x INTEGER NOT NULL,
    location_y INTEGER NOT NULL,
    location_z INTEGER NOT NULL,
    location_dimension VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX idx_projects_world_id ON projects(world_id);
CREATE INDEX idx_projects_type ON projects(type);
CREATE INDEX idx_projects_stage ON projects(stage);
CREATE INDEX idx_projects_location ON projects(location_x, location_y, location_z, location_dimension);
