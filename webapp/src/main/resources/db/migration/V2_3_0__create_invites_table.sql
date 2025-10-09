CREATE TABLE invites (
    id SERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    from_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    status_reached_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Create partial unique index to prevent duplicate pending invites only
CREATE UNIQUE INDEX idx_invites_unique_pending ON invites(world_id, to_user_id) WHERE status = 'PENDING';

-- Indexes for better performance
CREATE INDEX idx_invites_world_id ON invites(world_id);
CREATE INDEX idx_invites_from_user_id ON invites(from_user_id);
CREATE INDEX idx_invites_to_user_id ON invites(to_user_id);
CREATE INDEX idx_invites_status ON invites(status);
CREATE INDEX idx_invites_created_at ON invites(created_at);
