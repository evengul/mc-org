-- A declared project dependency records where it came from (MCO-302).
--
-- V2_7_0 created project_dependencies and an editor wrote to it. The editor was removed and the
-- table was not: every read path survived (GetProjectEdgesStep, GetProjectDependenciesStep,
-- RoadmapCycles, PendingFarmSupply), the route-scoped ProjectDependencyItemPlugin survived
-- installed on no route, and CacheManager kept onProjectDependencyCreated/Deleted for callers
-- that no longer existed. What did not survive was any way to add or remove a row.
--
-- That left one writer in src/main -- ImportIdeaPipeline -- so an idea import could assert a
-- dependency the application could not then take back. MCO-302 restores the editor; this column
-- is the one piece of new state it needs.
--
-- ---------------------------------------------------------------------------------------------
-- Why record the origin at all
--
-- It buys exactly one thing, and deliberately only one: an "Imported" badge, so a row you are
-- looking at says whether you asserted it or an idea import did. Per Even (2026-09-06), that is
-- the whole of the requirement -- there is no behaviour keyed off it. Nothing in the engine, the
-- roadmap layering or the cycle-breaking reads this column, and nothing should start without a
-- reason of its own: RoadmapCycles already treats *every* declared row as never-the-guess, and
-- that rule is about the row being declared, not about who declared it.
--
-- TEXT + CHECK rather than a Postgres enum, matching how the rest of this schema spells its
-- closed sets (see V2_61_0's note) -- an enum needs ALTER TYPE to grow.

ALTER TABLE project_dependencies
    ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'
        CHECK (origin IN ('MANUAL', 'IMPORTED'));

-- ---------------------------------------------------------------------------------------------
-- The backfill is knowable, not a guess
--
-- Every row currently in this table was written by ImportIdeaPipeline, because since the editor
-- was removed it has been the only INSERT in src/main. So 'IMPORTED' is not the convenient
-- default for existing rows, it is the true one -- the same standard V2_61_0 set for its own
-- backfill. New rows default to 'MANUAL' because from here the editor is the ordinary writer;
-- ImportIdeaPipeline names 'IMPORTED' explicitly rather than relying on a default.
--
-- Runs after the ADD COLUMN so the default has already populated the column, and before anything
-- can insert -- a migration holds the table.
UPDATE project_dependencies SET origin = 'IMPORTED';
