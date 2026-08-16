-- MCO-401: the quantity above which a raw material is worth a farm, per world.
--
-- Default 1,728 — one shulker box. Players do not think in "is 40,000 a lot"; they think
-- "that is more than a shulker of cobble", so the unit is the one already in their head.
--
-- World-scoped rather than global because the number is a judgement about a world's scale:
-- a superflat testing world and a 500,000-item megabase do not want the same line. It is not
-- per-user, because the demand it classifies belongs to the world's projects, not to whoever
-- is looking at them.
ALTER TABLE world
    ADD COLUMN farm_scale_threshold INT NOT NULL DEFAULT 1728;

-- 0 or negative would mark every raw material farm-scale, which is the same as marking none.
ALTER TABLE world
    ADD CONSTRAINT world_farm_scale_threshold_positive CHECK (farm_scale_threshold > 0);
