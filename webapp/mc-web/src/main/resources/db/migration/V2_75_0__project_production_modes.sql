-- Runtime production modes reach the project, with one active (MCO-413).
--
-- An idea's modes stopped at the project boundary: importing copied one mode's rates flat into
-- project_productions, and the project forgot which mode they came from. For a *runtime* mode that
-- is the wrong shape. Flip the fortress farm's wither-skeleton filter, or replant the tree farm with
-- oak, and nothing about the build changes — but the only way to tell Seam was to re-type the rates
-- of a mode the idea already knew. Build-time modes (V2_61_0) are a choice made once at import and
-- stay flattened; only runtime modes come here.
--
-- ## NULL mode_id is the list a project has without modes
--
-- Same convention as idea_item_requirements.mode_id (V2_61_0):
--
--   mode_id IS NULL      the project's one list — a farm recorded by hand (MCO-298), or an import
--                        whose idea has a single mode or build-time modes only. Every existing row.
--   mode_id IS NOT NULL  one runtime mode's rates. A project with modes has no NULL rows.
--
-- Rather than an implicit "Default" mode per project: every farm in the database today was
-- recorded by hand and has one list, and a mode row for each would put a concept on them that their
-- owners never met, for no reader that needs it. The "no NULL rows beside modes" invariant is
-- asserted in application code, like V2_61_0's runtime-modes-own-no-requirements — a CHECK cannot
-- see across rows.

CREATE TABLE project_production_modes
(
    id         SERIAL PRIMARY KEY,
    project_id INT     NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    -- Copied from the idea at import, not referenced: idea modes are deleted and re-inserted on
    -- every idea save, so their ids are not stable, and a design update reaching every world that
    -- built it would change a farm's supply under its owner. Requirements already copy the same way.
    name       TEXT    NOT NULL,
    position   INT     NOT NULL DEFAULT 0,
    active     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (project_id, name)
);

-- Exactly one active mode is the application's job (a switch is one UPDATE that sets it); at most
-- one is the database's.
CREATE UNIQUE INDEX project_production_modes_one_active_idx
    ON project_production_modes (project_id)
    WHERE active;

ALTER TABLE project_productions
    ADD COLUMN mode_id INT NULL REFERENCES project_production_modes (id) ON DELETE CASCADE;

CREATE INDEX project_productions_mode_id_idx ON project_productions (mode_id);

-- (project_id, item_id) cannot stay unique once two modes make the same item — the tree farm's seven
-- modes all make sticks and saplings. It becomes the same pair of partial indexes V2_61_0 used:
--   - at most one row per item in a project's mode-less list
--   - at most one row per item in each mode
ALTER TABLE project_productions
    DROP CONSTRAINT uq_project_productions_project_item;

CREATE UNIQUE INDEX project_productions_base_item_idx
    ON project_productions (project_id, item_id)
    WHERE mode_id IS NULL;

CREATE UNIQUE INDEX project_productions_mode_item_idx
    ON project_productions (mode_id, item_id)
    WHERE mode_id IS NOT NULL;

-- What a project supplies right now: its mode-less list, or its active mode's rates.
--
-- A view, so the rule lives in one place. Seven readers ask "what does this farm supply" (planner
-- supply, assumed-farm scenarios, planned-farm notices, roadmap edges, scenario keys, invalidation)
-- and each restating the filter is how two of them would come to disagree. The few readers that
-- want every mode — "could this farm make X if switched", "which stored ids does a version change
-- strand" — read project_productions directly.
CREATE VIEW active_project_productions AS
SELECT pp.*
FROM project_productions pp
         LEFT JOIN project_production_modes m ON m.id = pp.mode_id
WHERE pp.mode_id IS NULL
   OR m.active;
