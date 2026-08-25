-- MCO-460: which of two mutually-supplying farms comes first.
--
-- Farm supply edges (MCO-288) are derived from real data: producer produces what consumer
-- demands. Two farms that each consume a farm-scale amount of the other's output therefore
-- close a genuine loop, and both edges are true. The cobblestone farm really does need
-- gunpowder; the witch farm really does need cobblestone. There is no bad data to clean up —
-- the cycle is a fact about the world, and the resolution is a sequencing decision a human
-- makes.
--
-- Most such loops are broken before they get here, by the farm-scale threshold (MCO-401):
-- an edge carrying less than the world's threshold is a footnote, not a prerequisite, and is
-- not drawn at all. What survives is two farms genuinely waiting on each other, and only a
-- person can say which to build first.
--
-- ## What a row means
--
-- One row sets aside one derived supply edge: "the consumer comes first, so this edge does
-- not sequence the pair." It never adds an ordering — `project_dependencies` is where a
-- declared prerequisite lives (MCO-302). This is the opposite operation, and the two must not
-- be conflated: a row here is a *subtraction* from the derived graph.
--
-- Scoped to a pair rather than to a cycle. A cycle's membership changes as demand changes,
-- so keying on the cycle would invalidate the answer every time a plan is re-derived, whereas
-- "cobblestone before witch" stays true until the user says otherwise.
CREATE TABLE roadmap_cycle_order (
    id SERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world (id) ON DELETE CASCADE,
    -- The project the user said comes first. Its demand on the other is set aside.
    consumer_project_id INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    -- The project whose output the consumer is no longer sequenced behind.
    producer_project_id INTEGER NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One answer per ordered pair. Choosing the other direction replaces this row rather than
    -- adding a second, or the pair would have both edges set aside and no ordering at all.
    UNIQUE (consumer_project_id, producer_project_id),
    -- A project cannot come before itself, and a self-supply edge is already excluded upstream.
    CONSTRAINT roadmap_cycle_order_distinct CHECK (consumer_project_id <> producer_project_id)
);

-- The edge query filters by world and then by pair, in that order.
CREATE INDEX idx_roadmap_cycle_order_world ON roadmap_cycle_order (world_id);
