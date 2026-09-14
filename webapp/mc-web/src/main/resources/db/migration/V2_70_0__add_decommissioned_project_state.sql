-- A project can be decommissioned (MCO-541): it was built, it worked, and it no longer supplies
-- anything — the base moved, or a version upgrade broke the farm.
--
-- WHY A STATE AND NOT ARCHIVED
-- ---------------------------
-- ARCHIVED already stops a project counting as supply, but it also holds projects that were never
-- started, so a farm that ran for months would share a bucket with a draft nobody built. It can
-- only return to PENDING, and it records nothing about why.
--
-- The reason and the date are set when a project enters DECOMMISSIONED and left in place when it
-- leaves: the record that it once stopped is history, not state. The page shows them only while
-- the project is decommissioned.
ALTER TABLE projects DROP CONSTRAINT chk_projects_state;
ALTER TABLE projects ADD CONSTRAINT chk_projects_state
    CHECK (state IN ('PENDING', 'ACTIVE', 'PAUSED', 'DONE', 'CANCELLED', 'ARCHIVED', 'DECOMMISSIONED'));

ALTER TABLE projects ADD COLUMN decommission_reason VARCHAR(200);
ALTER TABLE projects ADD COLUMN decommissioned_at TIMESTAMP WITH TIME ZONE;
