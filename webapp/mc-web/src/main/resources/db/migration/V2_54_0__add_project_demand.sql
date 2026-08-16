-- MCO-316: per-project derived demand, materialised.
--
-- Farm supply edges used to match a producer's project_productions against the consumer's
-- *declared* resource_gathering rows. For an imported schematic every declared row is a
-- finished placed block, so the raw materials the build actually consumes exist only in the
-- derived plan. On the YAMS import that meant the Cobblestone Generator showed as supplying
-- one decorative cobblestone against 74,564 of real demand, and the Gold Farm — 7,299 units
-- of real demand — produced no edge at all, purely because no literal gold nugget is placed.
--
-- Deriving a plan per project on every roadmap load is what the original design avoided, so
-- the derivation is stored instead: written whenever a plan is derived anyway (the project
-- page), read by the roadmap. project_demand_state carries a fingerprint of the plan's inputs
-- so a stored row set can be recognised as stale without re-running the planner.
--
-- This is a cache, not a source of truth: everything here is recomputable from
-- resource_gathering + productions + overrides + the version's item graph.

CREATE TABLE project_demand
(
    project_id     INT    NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    -- May be a tag id ("#minecraft:planks") as well as an item id. Tags never match a
    -- production row, which is correct — you cannot produce a tag — but they are stored so
    -- the table is the whole derived demand rather than a filtered view of it.
    item_id        TEXT   NOT NULL,
    item_name      TEXT   NOT NULL,
    quantity       BIGINT NOT NULL,
    -- ActivityGroup and PlanNodeStatus as written by the engine. Consumers filter on these:
    -- farm edges want anything a project produces, MCO-401's farm-scale marker wants
    -- RAW_GATHER only.
    activity_group TEXT   NOT NULL,
    node_status    TEXT   NOT NULL,
    PRIMARY KEY (project_id, item_id)
);

-- The roadmap's lookup direction: "who demands this produced item?"
CREATE INDEX project_demand_item_id_idx ON project_demand (item_id);

CREATE TABLE project_demand_state
(
    project_id  INT PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    -- Hash of everything the derivation reads. Recomputing it is cheap; re-running the
    -- planner is not, so this is what decides whether stored demand can be trusted.
    fingerprint TEXT        NOT NULL,
    derived_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
