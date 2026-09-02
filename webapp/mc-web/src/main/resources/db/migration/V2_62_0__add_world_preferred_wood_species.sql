-- MCO-409: which tree this world farms, so the plan stops asking three times.
--
-- A YAMS-sized import opens with `#planks`, `#wooden_slabs` and `#logs` as three separate
-- variant questions. They are three askings of one thing — nobody holds three independent
-- opinions about which tree they are farming — and this is where that one answer lives.
--
-- **It defaults recipe ingredients, never the goal list.** A target is always a concrete item
-- (0 of 1,116 resource_gathering rows name a tag), so every tag this settles is something a
-- recipe consumes and nothing a player placed. Deciding to farm birch does not repaint a build
-- that asked for oak planks.
--
-- World-scoped for the same reason as farm_scale_threshold (V2_56_0): it is a fact about the
-- world's infrastructure, not about whoever is looking at it. A project that wants a different
-- wood overrides per tag through resource_gathering_plan_override, which already wins over this.
--
-- NULL, not a default species: which wood you farm is a real preference, and assuming one would
-- silently decide it. NULL leaves those tags open and asked, exactly as today.
ALTER TABLE world
    ADD COLUMN preferred_wood_species TEXT NULL;

-- The vocabulary is MemberPrior.SPECIES, which is the engine's own list and the one any UI
-- renders. Constrained here too so a bad write cannot reach the planner as a species that
-- matches no tag member and silently does nothing.
ALTER TABLE world
    ADD CONSTRAINT world_preferred_wood_species_known CHECK (
        preferred_wood_species IS NULL OR preferred_wood_species IN (
            'oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak',
            'mangrove', 'cherry', 'pale_oak', 'bamboo', 'crimson', 'warped'
        )
    );
