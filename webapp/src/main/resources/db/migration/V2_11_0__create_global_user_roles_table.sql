-- Create global user roles table
-- This table stores global roles that apply system-wide, separate from world-specific roles
-- Supported roles (expandable): superadmin, banned, moderator, idea_creator

CREATE TABLE global_user_roles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    granted_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE, -- NULL means no expiration
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for better performance
CREATE INDEX idx_global_user_roles_user_id ON global_user_roles(user_id);
CREATE INDEX idx_global_user_roles_role ON global_user_roles(role);
CREATE INDEX idx_global_user_roles_active ON global_user_roles(is_active);
CREATE INDEX idx_global_user_roles_expires_at ON global_user_roles(expires_at);

-- Composite index for common queries (active roles for a user)
CREATE INDEX idx_global_user_roles_user_active ON global_user_roles(user_id, is_active);

-- Add a unique constraint to prevent duplicate active roles for the same user
-- Note: This allows the same role to be granted multiple times if previous ones are inactive
CREATE UNIQUE INDEX idx_global_user_roles_unique_active
ON global_user_roles(user_id, role)
WHERE is_active = TRUE;
