-- A Done farm supplies every runtime mode's items; Seam no longer tracks which mode is running (MCO-588).
--
-- V2_76_0 gave a project's runtime modes one active row, and only that mode supplied. It was state
-- the player had to keep in step with the game by hand, which Seam cannot see, and it made plans in
-- one world compete for one farm: a plan wanting oak and a plan wanting cherry could not both be
-- supplied by the tree farm, although in the game both are — run Oak Mode for a while, then Cherry.
-- A *runtime* mode is cheap to switch by definition, so under unbounded supply (rates are
-- information, never scheduled against) a farm supplies everything any of its modes makes.
--
-- The modes themselves stay: they are the design's own record of what each way of running the farm
-- makes and at what rate, and "which mode, for how long" is built on them later (MCO-595).

DROP VIEW active_project_productions;

DROP INDEX project_production_modes_one_active_idx;

ALTER TABLE project_production_modes
    DROP COLUMN active;

-- What a project supplies: one row per item, whichever modes make it.
--
-- A view, so the de-duplication lives in one place. Several modes making one item is normal (the tree
-- farm's seven all make sticks and saplings; Oak and Azalea both make Oak Log), and a reader joining
-- project_productions directly would draw one roadmap edge, count one produced item, per mode. Readers
-- that want the rows themselves — the panel, what each mode makes, rates — read project_productions.
CREATE VIEW project_supplied_items AS
SELECT DISTINCT project_id, item_id
FROM project_productions;
