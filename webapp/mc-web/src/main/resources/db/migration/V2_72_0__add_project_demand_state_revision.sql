-- MCO-578: which revision of the derivation wrote a project's stored demand.
--
-- The fingerprint is only compared where demand is written (the project page). The roadmap only
-- asks whether a state row exists, so a change to the planner itself never reached it: every
-- stored plan kept being served until its project page was opened. Comparing this column with the
-- code's DemandFingerprint.REVISION makes such a row count as not yet derived.
--
-- Existing rows get 0, which no revision of the code uses. Nothing records which planner derived
-- them, so they are re-derived once, by the next roadmap load of their world.
ALTER TABLE project_demand_state
    ADD COLUMN revision INT NOT NULL DEFAULT 0;
