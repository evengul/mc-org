-- Container tags (MCO-530) — Shared Storage phase A.
--
-- A tagged container belongs to a project, and that assignment lives here rather than in the
-- client mod's per-world file: a client-local tag is invisible to the thing that reads the chest.
-- Storing it server-side is also what makes tags *shared* — everyone tagging into the same world
-- sees the same tags, with no client-to-client anything.
--
-- One row per tagged BLOCK POSITION. A joined double chest is two rows sharing a group_key: both
-- halves are stored so either one can render a label and breaking one half does not orphan the
-- tag, while the sweep (phase B) dedupes by group_key so contents are not counted twice.

CREATE TABLE container_tags (
    id BIGSERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    -- One container, one project. Re-tagging replaces project_id on the existing row.
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    dimension VARCHAR(64) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    -- Shared by both halves of a double chest; the position itself ("x,y,z") otherwise.
    group_key VARCHAR(128) NOT NULL,
    -- A whitelist, not "anything that is an Inventory": tagging a furnace would count its fuel and
    -- input, which is not what anyone means. Ender chests are per-player and out of scope.
    kind VARCHAR(32) NOT NULL CHECK (kind IN (
        'chest', 'trapped_chest', 'barrel', 'shulker_box', 'hopper', 'dropper', 'dispenser'
    )),
    -- Who tagged it, for the webapp's list. A deleted user leaves the tag standing.
    tagged_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    tagged_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Both columns below are written ONLY by the reporter (phase B). The webapp never guesses them;
    -- it only sets the initial value, which is the honest one for a container nothing has read yet.
    last_seen_at TIMESTAMP WITH TIME ZONE,
    state VARCHAR(16) NOT NULL DEFAULT 'unreadable'
        CHECK (state IN ('ok', 'unreadable', 'missing')),

    -- Re-tagging a position is an upsert of project_id, not a second row.
    CONSTRAINT uq_container_tags_position UNIQUE (world_id, dimension, x, y, z)
);

CREATE INDEX idx_container_tags_world_id ON container_tags(world_id);
CREATE INDEX idx_container_tags_project_id ON container_tags(project_id);
CREATE INDEX idx_container_tags_group_key ON container_tags(world_id, group_key);
