-- MCO-572: a second, cached demand set — a project's plan derived as if some unfinished farms
-- were already producing.
--
-- The roadmap's final-project panels split "by hand now" into what the farms still to build
-- would take off the list ("promised") and what stays "yours either way". Both are differences
-- between two hand lists, and the second list needs a plan of its own: seconds per final
-- project, so it is cached here rather than derived on every page load. The STOPPED section's
-- "what no other farm covers" is the same kind of difference, with one decommissioned farm
-- running again on top.
--
-- A scenario is keyed by `scenario_key`: a hash of the farms assumed built *and* the items each
-- of them produces, so editing an assumed farm's productions is a different scenario rather
-- than a stale one. Rows are the plan's activity list, exactly as `project_demand` stores it.

CREATE TABLE project_demand_scenario_state
(
    project_id       INT         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    scenario_key     TEXT        NOT NULL,
    -- The project's own `project_demand_state.fingerprint` when this scenario was derived. The
    -- scenario is only trusted while that fingerprint is unchanged, so it goes stale on exactly
    -- the events the main demand does — a supply change deletes the main fingerprint, a
    -- re-derivation on the project page replaces it.
    base_fingerprint TEXT        NOT NULL,
    derived_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, scenario_key)
);

CREATE TABLE project_demand_scenario
(
    project_id     INT    NOT NULL,
    scenario_key   TEXT   NOT NULL,
    item_id        TEXT   NOT NULL,
    item_name      TEXT   NOT NULL,
    quantity       BIGINT NOT NULL,
    activity_group TEXT   NOT NULL,
    node_status    TEXT   NOT NULL,
    PRIMARY KEY (project_id, scenario_key, item_id),
    FOREIGN KEY (project_id, scenario_key)
        REFERENCES project_demand_scenario_state (project_id, scenario_key) ON DELETE CASCADE
);
