-- MCO-506: make an override row say "the planner was wrong *here*", not just "the user picked X".
--
-- The table is the product's only record of a planning disagreement. It stored the user's answer
-- and nothing else, so afterwards it was impossible to tell whether the user had corrected the
-- planner, agreed with it, or answered a question the planner had no opinion on at all. That
-- distinction is the entire value of the dataset. Two additive columns fix it:
--
--   planner_pick   what the planner (or, for an open tag, the ranking the picker shows) had
--                  selected at the moment the user answered. A row is now a *diff*: answer vs
--                  planner_pick. "Which paths get changed the most" becomes a query.
--   superseded_at  history. An override was one row per (project_id, item_id), replaced on
--                  re-answer — which destroyed the second answer, and a second answer to the same
--                  question is a much stronger signal than the first. Re-answering now stamps the
--                  old row and inserts a new one; superseded_at IS NULL is the live row.
--
-- NO BACKFILL. The 16 rows that exist when this runs pre-date the instrumentation and cannot get
-- a planner pick retroactively — the plan they were answered against is long gone. They keep
-- planner_pick NULL, which reads correctly as "unknown", not as "the user agreed".
--
-- NOT STORED, DELIBERATELY: the picks the planner made that the user left alone. The plan is
-- re-derived from the graph on every read, so "what did the planner pick for every node" is
-- computable on demand; storing it would be a firehose (every plan render, every node) for data
-- that is already free. See GetPlanOverridesStep / PlanOverrideSteps.kt for the same note where a
-- future reader would otherwise be tempted to add the logging.
--
-- The dataset's headline query — which questions get overridden most, and to what:
--
--   SELECT item_id,
--          COUNT(*)                                            AS answers,
--          COUNT(*) FILTER (WHERE superseded_at IS NOT NULL)   AS re_answered,
--          COUNT(*) FILTER (WHERE planner_pick IS NOT NULL
--                             AND planner_pick IS DISTINCT FROM COALESCE(tag_member, source_key))
--                                                              AS corrections,
--          COUNT(DISTINCT COALESCE(tag_member, source_key))    AS distinct_answers,
--          string_agg(DISTINCT COALESCE(tag_member, source_key), ', ') AS answers_given
--   FROM resource_gathering_plan_override
--   GROUP BY item_id
--   ORDER BY answers DESC, item_id;

ALTER TABLE resource_gathering_plan_override
    ADD COLUMN planner_pick  VARCHAR,
    ADD COLUMN superseded_at TIMESTAMP;

-- The live-row guarantee, and the reason it has to change shape.
--
-- UNIQUE (project_id, item_id) cannot survive history: a superseded row and the answer that
-- replaced it share both columns. Dropping it outright would leave "exactly one live row per
-- question" as a convention the read path merely hopes for. A PARTIAL unique index keeps the
-- guarantee structural — a second live row for the same question is rejected by the database —
-- while letting any number of superseded rows accumulate behind it.
ALTER TABLE resource_gathering_plan_override
    DROP CONSTRAINT unique_override_per_project_item;

CREATE UNIQUE INDEX unique_live_override_per_project_item
    ON resource_gathering_plan_override (project_id, item_id)
    WHERE superseded_at IS NULL;

COMMENT ON COLUMN resource_gathering_plan_override.planner_pick IS
    'What the planner would have picked for this item when the user answered (member item id, or source key for a pin). NULL on the pre-MCO-506 rows, which cannot be backfilled.';
COMMENT ON COLUMN resource_gathering_plan_override.superseded_at IS
    'When a later answer to the same question replaced this row. NULL = the live row; exactly one per (project_id, item_id), enforced by unique_live_override_per_project_item.';
COMMENT ON TABLE resource_gathering_plan_override IS
    'User-pinned planning choices per project item, with history. Plans are re-derived from the live rows by the engine; superseded rows exist only as the record of where the planner was disagreed with.';
