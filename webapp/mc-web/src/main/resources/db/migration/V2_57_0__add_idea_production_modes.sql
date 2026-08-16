-- What an idea produces, relationally — modes and their rates.
--
-- Until now this lived in `ideas.category_data -> 'productionRate'` as untyped JSON on the FARM
-- category. Three problems with that, and MCO-294 hits all of them: it cannot be joined against
-- demand, it is category-gated (a mob farm inside a storage build produces nothing, as far as the
-- schema is concerned), and its only reader has been broken since MCO-204 changed the shape under
-- it — no idea import has ever written a production row (MCO-411). So there is no data to
-- preserve here, only a shape to replace.
--
-- ## Modes
--
-- A farm can run in more than one way, and the ways produce different things at different rates:
-- an ice farm at full speed or slowed for lag; a nether fortress farm with a wither-skeleton
-- filter on or off, times three speeds. Modes are **flat**, deliberately — the fortress farm is
-- six modes, not two axes multiplied. Axes would model that one case more elegantly and every
-- other case worse, and the six-mode farm is rare.
--
-- Most farms have exactly one mode with one set of items. That case must stay free: the form
-- creates a single mode without ever saying the word, and mode UI appears only on the second one.
-- The nesting this replaces was removed once already (MCO-204) for being "the single most tedious
-- thing in the form"; re-introducing it only works if the common case never pays for it.
CREATE TABLE idea_production_modes
(
    id       SERIAL PRIMARY KEY,
    idea_id  INT  NOT NULL REFERENCES ideas (id) ON DELETE CASCADE,
    -- Names the way the farm is run ("Max speed", "Skeletons only, slow"). Unique per idea so a
    -- mode can be referred to by name — a project records which one it was built to run.
    name     TEXT NOT NULL,
    -- Author's ordering. Not a ranking: which mode is *best* depends on the demand being matched,
    -- so that is computed, never stored.
    position INT  NOT NULL DEFAULT 0,
    UNIQUE (idea_id, name)
);

CREATE INDEX idea_production_modes_idea_id_idx ON idea_production_modes (idea_id);

CREATE TABLE idea_production_rates
(
    mode_id       INT  NOT NULL REFERENCES idea_production_modes (id) ON DELETE CASCADE,
    item_id       TEXT NOT NULL,
    -- Items per hour. Matches project_productions.rate_per_hour, which is what an import writes
    -- into once a mode is chosen.
    rate_per_hour INT  NOT NULL CHECK (rate_per_hour >= 0),
    PRIMARY KEY (mode_id, item_id)
);

-- MCO-294's lookup direction: "which ideas produce this item, and how fast at best?"
CREATE INDEX idea_production_rates_item_id_idx ON idea_production_rates (item_id);
