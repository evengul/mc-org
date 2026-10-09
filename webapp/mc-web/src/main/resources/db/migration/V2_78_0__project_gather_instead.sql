-- A farm still to build that the world has decided to gather by hand for now (MCO-574).
--
-- The roadmap's TO BUILD table offers `gather instead ▸` on every farm: take it out of the plan, and
-- its items onto the hand list. Its items are already on the hand list — a plan is supplied by DONE
-- farms only — so what the decision changes is everything that treats an unbuilt farm as *promised*:
-- its supply edges (it stops being anybody's prerequisite, and stops waiting on anybody), the TO
-- BUILD table, and the roadmap's "promised" share of each final project's hand list. Nothing here is
-- read by plan derivation, so a re-derived plan cannot undo it.
--
-- Not a state: PENDING/ACTIVE/PAUSED keep meaning what they meant, and CANCELLED or ARCHIVED would
-- say the farm is abandoned. The user is saying "not now", and the farm stays a farm they could still
-- build. Not world_farm_dismissals either: that dismisses a *suggestion*, and this is a project.
--
-- One row per farm, world-wide: the TO BUILD row is per farm and names every final project it feeds.
CREATE TABLE project_gather_instead
(
    project_id INT         NOT NULL PRIMARY KEY REFERENCES projects (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The decision only means something about a farm still to build. A farm that is built supplies
-- whatever was decided, and one cancelled, archived or decommissioned is out of the plan already —
-- so leaving the three unfinished states clears it, through whichever door the state changed. Kept,
-- it would come back to life the day the farm was reopened, as a choice nobody remembers making.
CREATE FUNCTION clear_gather_instead_when_finished() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.state NOT IN ('PENDING', 'ACTIVE', 'PAUSED') THEN
        DELETE FROM project_gather_instead WHERE project_id = NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER projects_clear_gather_instead
    AFTER UPDATE OF state ON projects
    FOR EACH ROW
    WHEN (OLD.state IS DISTINCT FROM NEW.state)
EXECUTE FUNCTION clear_gather_instead_when_finished();
