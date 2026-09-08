-- Container contents and the measurement rollup (MCO-532) — Shared Storage phase A.
--
-- This is what makes the shared count exist.
--
-- WHY THE WEBAPP STORES PER-CONTAINER CONTENTS RATHER THAN TOTALS
-- ---------------------------------------------------------------
-- The reporter could keep a running picture and push totals. It must not, for one reason: a
-- container in an unloaded chunk still counts. Its contents cannot have changed — nobody was there
-- to change them — so its last reading is correct data, and something has to remember it between
-- sweeps. Putting that memory here makes the REPORTER STATELESS: a restart costs one full re-push,
-- not a resync protocol. It also gives phase E real material — "which four chests is this 12 coming
-- from, and when was each last seen".

CREATE TABLE container_contents (
    container_tag_id BIGINT NOT NULL REFERENCES container_tags(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL CHECK (count >= 0),
    -- When the sweep that produced this reading ran. Kept per row so the rollup can report the
    -- OLDEST contributing reading, which is what tells a player how much to trust the number.
    seen_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- One row per item per container. A push replaces the whole set for the containers it names.
    PRIMARY KEY (container_tag_id, item_id)
);

CREATE INDEX idx_container_contents_item_id ON container_contents(item_id);

-- The rolled-up number the HUD reads.
--
-- Derivable from container_contents by summing over a project's tags, and MATERIALISED anyway
-- because the HUD polls it roughly every 10s per player and that join is not free. Recomputed on
-- every contents push and on tag delete — never trusted to drift.
--
-- NOTE resource_gathering_progress.collected is NOT this, is not derived from this, and is not
-- touched by anything that writes this. `collected` is the human's number and stays the progress
-- truth; `measured` is evidence. Showing the disagreement, and letting someone adopt it, is phase
-- E's drift model (MCO-539/MCO-540). Conflating them here by accident is the failure this comment
-- exists to prevent.
CREATE TABLE resource_gathering_measurement (
    project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    item_id VARCHAR(255) NOT NULL,
    -- Sum over contributing containers. BIGINT because plan quantities are Long end to end and a
    -- shared base can hold more than an Int of a cheap item.
    measured BIGINT NOT NULL,
    -- How many containers contributed to `measured` — i.e. readable ones. Containers in state
    -- 'unreadable' or 'missing' are excluded from both; their story is told from container_tags,
    -- which is where their state lives.
    container_count INTEGER NOT NULL,
    -- The oldest contributing reading. Null when nothing contributes.
    oldest_seen_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (project_id, item_id)
);

-- The HUD's frequent poll is world-wide: one indexed read joined to projects, no plan derivation
-- and no per-project assembly.
CREATE INDEX idx_rgm_project_id ON resource_gathering_measurement(project_id);
