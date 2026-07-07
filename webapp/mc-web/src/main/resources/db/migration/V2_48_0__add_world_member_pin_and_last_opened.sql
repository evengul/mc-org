-- Per-user world affordances for the redesigned Worlds page:
--   * pinned         — user pins a world to sort it above the rest (favourite).
--   * last_opened_at — when this user last opened the world; drives "opened Xh ago"
--                      and the default most-recently-opened-first sort.
-- Both are per (user, world), so they live on world_members, not on world.
ALTER TABLE world_members ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE world_members ADD COLUMN last_opened_at TIMESTAMP WITH TIME ZONE;

-- Seed last_opened_at so existing memberships sort sensibly before the first open.
UPDATE world_members SET last_opened_at = updated_at;

-- Supports the "pinned first, then most-recently-opened" ordering of a user's worlds.
CREATE INDEX idx_world_members_user_sort
    ON world_members(user_id, pinned DESC, last_opened_at DESC);
