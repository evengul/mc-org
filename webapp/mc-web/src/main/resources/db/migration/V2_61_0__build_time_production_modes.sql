-- Modes get a kind, and build-time modes get their own material lists (MCO-463).
--
-- V2_57_0 gave an idea production *modes* and modelled the half of them that varies: rates. It
-- assumed one sentence, which MCO-413 was then built on — "a mode is a runtime option, not a
-- build-time one". MCO-439 finding 1 found the counterexample while sourcing by hand: a cobblestone
-- farm published as *single module / 4 modules* × *with / without storage*. All four are chosen
-- when you build the thing, and 924k/h ÷ 231k/h = 4.0 exactly — the rate spread *is* the module
-- count, a consequence of a build decision rather than a runtime one.
--
-- ## The discriminator is not switchability, it is what the mode changes
--
--   runtime mode     changes what the farm supplies
--   build-time mode  changes what it supplies AND what it costs to build
--
-- The first half already has a home (idea_production_rates, per mode). The second does not:
-- idea_item_requirements is PRIMARY KEY (idea_id, item_id) — one flat list per idea. So the four
-- modes above were entered against one shared material list, and three of the four are wrong in
-- the bank right now. A build-time mode is currently unrepresentable, not merely awkward.

-- ---------------------------------------------------------------------------------------------
-- 1. Modes declare their kind.
--
-- TEXT + CHECK rather than a Postgres enum, matching how the rest of this schema spells its closed
-- sets: an enum needs ALTER TYPE to grow, and this one may well grow (nothing says build-time and
-- runtime are the only two axes a design can have).
--
-- DEFAULT 'RUNTIME' is the honest backfill, not merely the convenient one. Every existing mode was
-- entered under a form that only ever described ways of *running* a farm, so runtime is what those
-- authors were answering. Anything else would attribute a choice to them they were never offered.
ALTER TABLE idea_production_modes
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'RUNTIME'
        CHECK (kind IN ('BUILD_TIME', 'RUNTIME'));

-- MCO-413 reads this to decide which modes follow an import to the project: runtime modes reach
-- project_productions and stay switchable, build-time modes are resolved at import and do not.
CREATE INDEX idea_production_modes_kind_idx ON idea_production_modes (idea_id, kind);

-- ---------------------------------------------------------------------------------------------
-- 2. A build-time mode carries its own material list.
--
-- ## Whole lists, not deltas
--
-- Finding 1 left this open ("gains the mode dimension, or a mode-scoped sibling holds the deltas")
-- and deltas looked attractive: the storage variant reads like "+N hoppers, +M chests" against a
-- base. Rejected on how the data actually arrives. A build-time variant is a *different .litematic
-- file* — the 4-module farm is its own download, parsed by its own upload (MCO-414's multi-file
-- import already handles exactly this). Whole lists are what the front door produces; a delta would
-- have to be computed against a base nobody entered, and would then have to be un-computed for
-- every consumer that wants a material list. Deltas save typing that nobody was going to do
-- anyway.
--
-- ## NULL mode_id is the base list, and it is what every existing row is
--
--   mode_id IS NULL      the idea's one list — a non-farm idea, or a farm with no build-time
--                        variants. The shape this table has always had.
--   mode_id IS NOT NULL  that build-time mode's own list, *replacing* the base rather than adding
--                        to it. An idea with build-time modes has no NULL rows.
--
-- Replacement, not addition, for the same reason deltas lost: each variant's list is complete as
-- captured. It also keeps the read trivial — one list per idea, or one per chosen mode, never a
-- merge at read time.
--
-- Nullable rather than requiring every idea to own a mode: an idea with no productions at all (a
-- build, a storage hall) still has requirements, and inventing a mode row to hang them off would
-- put a farm concept on ideas that are not farms.
--
-- Runtime modes never own requirements — that is the whole distinction — and this is asserted in
-- application code rather than here. A CHECK cannot see across to idea_production_modes.kind, and
-- a trigger for one invariant that the two write paths already enforce would cost more to keep
-- honest than it is worth.
ALTER TABLE idea_item_requirements
    ADD COLUMN mode_id INT NULL REFERENCES idea_production_modes (id) ON DELETE CASCADE;

-- The PK cannot survive a nullable member (Postgres will not have NULL in a primary key), so it
-- becomes two partial unique indexes that together say what it said, per list:
--   - at most one row per item in the base list
--   - at most one row per item in each build-time mode's list
ALTER TABLE idea_item_requirements
    DROP CONSTRAINT idea_item_requirements_pkey;

CREATE UNIQUE INDEX idea_item_requirements_base_item_idx
    ON idea_item_requirements (idea_id, item_id)
    WHERE mode_id IS NULL;

CREATE UNIQUE INDEX idea_item_requirements_mode_item_idx
    ON idea_item_requirements (mode_id, item_id)
    WHERE mode_id IS NOT NULL;

-- idx_idea_item_requirements_idea_id (V2_16_2) still serves "everything this idea requires", which
-- is now the read that has to span both kinds of row, so it is left alone.
CREATE INDEX idea_item_requirements_mode_id_idx ON idea_item_requirements (mode_id);

-- No backfill. Every existing row keeps mode_id NULL and means exactly what it meant yesterday:
-- this idea's material list. Build-time modes only become expressible from here on, and the
-- cobblestone farm that prompted this gets re-entered by hand (MCO-463's last acceptance
-- criterion) rather than guessed at by a migration that cannot know which of its four variants the
-- one stored list describes.
