-- MCO-584: an invalidation that lands while a derivation of the same project is running is no
-- longer overwritten by that derivation.
--
-- Until now every invalidation deleted the project's project_demand_state row, and the derivation
-- upserted it ~0.7 s later with the fingerprint of the inputs it read before the invalidation. The
-- plan of the old inputs was then stored as current, and the roadmap served it.
--
-- Invalidating now means nulling the fingerprint and bumping `generation`, inserting a row when
-- the project has none. A derivation reads the generation before it reads any input, and its save
-- only lands when the generation is still the one it read. The save's upsert locks the row, so an
-- invalidation still uncommitted at that point is waited for rather than overtaken.
--
-- A counter rather than a timestamp: an invalidation stamped before a derivation starts but
-- committed after it has read its inputs would carry an older time than the derivation's start,
-- and pass. A generation is only ever compared, never ordered, so commit order cannot fool it.
--
-- A NULL fingerprint is "not derived" to every reader, exactly as a missing row was: coverage
-- requires one, the project page's write-through compares against it, and a scenario is fresh only
-- while it equals its base.
--
-- derived_at goes nullable for the rows an invalidation inserts: a project never derived has no
-- derivation time, and the column's default would give it one. An invalidation of an existing row
-- leaves it alone, so it keeps saying when the last (now stale) plan was derived.
ALTER TABLE project_demand_state
    ALTER COLUMN fingerprint DROP NOT NULL,
    ALTER COLUMN derived_at DROP NOT NULL,
    ADD COLUMN generation BIGINT NOT NULL DEFAULT 0;

-- Same trigger, same bookkeeping-column rule (V2_73_0); only what "invalidate" does changes.
--
-- The INSERT selects from projects so a cascade does not resurrect a deleted project's state: when
-- a project is deleted, its resource_gathering rows go by ON DELETE CASCADE and fire this trigger
-- after the project row is gone, and a plain VALUES insert would fail the foreign key and with it
-- the delete.
CREATE OR REPLACE FUNCTION invalidate_project_demand() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(OLD) - TG_ARGV) = (to_jsonb(NEW) - TG_ARGV) THEN
        RETURN NULL;
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        INSERT INTO project_demand_state (project_id, fingerprint, derived_at, generation)
        SELECT p.id, NULL, NULL, 1 FROM projects p WHERE p.id = OLD.project_id
        ON CONFLICT (project_id) DO UPDATE
            SET fingerprint = NULL, generation = project_demand_state.generation + 1;
    END IF;
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.project_id <> OLD.project_id) THEN
        INSERT INTO project_demand_state (project_id, fingerprint, derived_at, generation)
        SELECT p.id, NULL, NULL, 1 FROM projects p WHERE p.id = NEW.project_id
        ON CONFLICT (project_id) DO UPDATE
            SET fingerprint = NULL, generation = project_demand_state.generation + 1;
    END IF;
    RETURN NULL;
END;
$$;
