-- MCO-578: what a project's stored demand was derived *by* and *from*, beyond its fingerprint.
--
-- The fingerprint is only compared where demand is written (the project page). The roadmap only
-- asks whether a state row is current, so two things no write to a world can announce never
-- reached it: a change to the planner itself, and a re-ingest of the same Minecraft version.
--
-- revision: the code's DemandFingerprint.REVISION when the row was written. Existing rows get 0,
-- which no revision of the code uses — nothing records which planner derived them — so they are
-- re-derived once, by the next roadmap load of their world.
--
-- game_data_epoch: the version's ingestion epoch (minecraft_version_ingestion.completed_at) the
-- derivation read. Compared with the ledger's current epoch rather than derived_at, because a plan
-- built on a graph cached from before a re-ingest is a plan of the old data however late it is
-- written. NULL when the version had no completed ledger row.
ALTER TABLE project_demand_state
    ADD COLUMN revision        INT NOT NULL DEFAULT 0,
    ADD COLUMN game_data_epoch TIMESTAMPTZ;
