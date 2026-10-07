-- MCO-578: a project's stored demand is dropped whenever one of its own plan inputs changes.
--
-- project_demand is a cache of a project's derived plan, and the roadmap trusts any project that
-- has a project_demand_state row. A project's own inputs — its targets, its collected counts, its
-- plan overrides and its links — are written by sixteen files, and several never re-derive
-- afterwards: the mod's sync, Field Log edits, adopting measurements, adding from a schematic. The
-- ON DELETE SET NULL that unlinks a requirement when its solving project is deleted runs no
-- application code at all. Since MCO-572 the roadmap's hand-list numbers come straight from these
-- rows, so each of those doors left a visible stale number.
--
-- A trigger rather than a call in every handler, because the rule is about the tables, not the
-- doors: it covers the FK cascade and whichever door is added next. It only deletes the state row.
-- project_demand stays readable, and the next roadmap load re-derives the project through the
-- fill-on-read path (or the project page does, through its write-through). Deriving writes none of
-- these tables, so the write-through cannot invalidate itself.
--
-- An UPDATE that changes only bookkeeping columns is not a change to the plan's inputs. Every
-- progress upsert stamps updated_at (and progress_source), and the mod's sync upserts every item it
-- carries whether its count moved or not, so a whole-row comparison would invalidate on every sync.
-- Each trigger names its bookkeeping columns as arguments, and everything else counts: a column
-- added later invalidates until someone decides it is bookkeeping, which is the safe default.
--
-- Cross-project inputs (farm supply) stay with the application's targeted invalidation
-- (DemandInvalidation.kt, MCO-404), and world-wide ones (version, wood species) with the steps that
-- change them; neither is a row in these tables.
CREATE FUNCTION invalidate_project_demand() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(OLD) - TG_ARGV) = (to_jsonb(NEW) - TG_ARGV) THEN
        RETURN NULL;
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        DELETE FROM project_demand_state WHERE project_id = OLD.project_id;
    END IF;
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.project_id <> OLD.project_id) THEN
        DELETE FROM project_demand_state WHERE project_id = NEW.project_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER resource_gathering_invalidates_demand
    AFTER INSERT OR UPDATE OR DELETE ON resource_gathering
    FOR EACH ROW EXECUTE FUNCTION invalidate_project_demand('created_at', 'updated_at');

CREATE TRIGGER resource_gathering_progress_invalidates_demand
    AFTER INSERT OR UPDATE OR DELETE ON resource_gathering_progress
    FOR EACH ROW EXECUTE FUNCTION invalidate_project_demand('created_at', 'updated_at', 'progress_source');

-- planner_pick records what the planner would have chosen (MCO-506); the plan reads tag_member.
CREATE TRIGGER resource_gathering_plan_override_invalidates_demand
    AFTER INSERT OR UPDATE OR DELETE ON resource_gathering_plan_override
    FOR EACH ROW EXECUTE FUNCTION invalidate_project_demand('created_at', 'updated_at', 'planner_pick');
