-- Base users table for Profile data
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- World members table for WorldMember data
CREATE TABLE world_members (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    display_name VARCHAR(255) NOT NULL,
    world_role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, world_id)
);

-- Minecraft profiles table
CREATE TABLE minecraft_profiles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uuid VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX idx_world_members_user_id ON world_members(user_id);
CREATE INDEX idx_world_members_world_id ON world_members(world_id);
CREATE INDEX idx_minecraft_profiles_user_id ON minecraft_profiles(user_id);
CREATE INDEX idx_minecraft_profiles_uuid ON minecraft_profiles(uuid);

