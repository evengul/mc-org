-- MCO-407: farm-scale suggestions a world has decided against.
--
-- The threshold (V2_56_0) is the only control the roll-up has, and it is blunt in one
-- direction: raising it to silence one wrong line silences every smaller correct one. Some
-- lines are wrong in ways no number expresses —
--
--   * cost blindness: the YAMS roll-up reads "2,413 Water". You do not build a water farm,
--     you build one bucket. (MCO-467 fixed *that* line at the source; the class of error it
--     belongs to is open-ended and cannot all be classified in advance.)
--   * a decision already made: "I buy this from a villager", "I have a double chest of it".
--   * not worth it here: farm-scale by count, trivial to gather in this particular world.
--
-- A row here removes one item from the roll-up and from the design suggestions, and clears
-- its row badge. That is all it does.
--
-- ## What this is not
--
-- **It is not a record of how the item is solved.** That lives elsewhere and is read by other
-- things: `resource_gathering.solved_by_project_id`, farm supply (MCO-288), the productions a
-- project declares (MCO-298). The tempting reading — "dismissed = I have a plan for it" —
-- would make this a second, weaker supply record that nothing else in the system consults, and
-- the roadmap would keep sequencing around demand a user believed they had answered here.
--
-- ## World-scoped
--
-- It overrides a world-level threshold and it fits the sentence that motivates it: "I do not
-- farm water, ever". The flaw is real and known: "not for *this build*" is a project-level
-- statement, and dismissing gravel because one project trades for it silences it for the
-- megabase next door. Project scope is the obvious extension — same table plus a nullable
-- project_id, world rows winning — and is deliberately not built until someone wants it,
-- because two scopes with no rule for which wins is worse than one scope that is sometimes
-- too broad.
--
-- ## Permanent until undone, and quantity is why that is safe
--
-- No re-suggestion rule ("dismissed at N, re-offer past 10N"). A dismissal that comes back on
-- its own is the threshold's failure in a new costume — you would dismiss it again, with no
-- memory of why you dismissed it the first time. Growth is *shown* instead: the quantity at
-- dismissal is kept, the ignored list prints it beside today's number, and a line that has
-- grown tenfold says so where the undo button already is.
CREATE TABLE world_farm_dismissals (
    id SERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world (id) ON DELETE CASCADE,
    item_id TEXT NOT NULL,
    -- Resolved server-side when the dismissal is made, so the ignored list has a label for an
    -- item that is no longer in any plan. Never read back into the planner.
    item_name TEXT NOT NULL,
    -- What the plan demanded when the call was made. Display only — see above.
    quantity_at_dismissal BIGINT NOT NULL,
    dismissed_by INTEGER REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One answer per item per world. Dismissing twice is the same decision, not two.
    UNIQUE (world_id, item_id)
);
