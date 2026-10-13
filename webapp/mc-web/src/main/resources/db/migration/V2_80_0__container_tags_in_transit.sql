-- A tagged shulker box a player has picked up is in transit, not missing (MCO-548).
--
-- A shulker box is the one container whose contents travel with it. The reporter stamps a tagged
-- box with its tag id as it drops and finds it again in players' inventories, so it can say who
-- has it and what is in it now. Until then a picked-up box read as 'missing', its contents were
-- cleared, and the count fell to zero while the stock was being carried to the build.
--
-- 'in_transit' counts toward the measurement exactly as 'ok' does; only where the stock is differs.

ALTER TABLE container_tags DROP CONSTRAINT container_tags_state_check;
ALTER TABLE container_tags ADD CONSTRAINT container_tags_state_check
    CHECK (state IN ('ok', 'unreadable', 'missing', 'in_transit'));

-- Who is carrying it. Written only by the reporter, like state. The name is for whoever is looking
-- for the stock; the uuid is identity, and it is how a restarted reporter tells "the carrier logged
-- off" (keep counting) from "the carrier is online and no longer has it" (missing).
ALTER TABLE container_tags ADD COLUMN carried_by_uuid UUID;
-- 64, not Minecraft's 16: a Floodgate or proxy prefix makes names longer, and a name that does not
-- fit must not be able to refuse the whole push it arrives in.
ALTER TABLE container_tags ADD COLUMN carried_by_name VARCHAR(64);

-- A carrier exactly when in transit, and always both halves of one: a stale name on an 'ok' chest
-- would tell someone to go and ask a player about stock that is sitting in storage.
ALTER TABLE container_tags ADD CONSTRAINT container_tags_carried_by_check
    CHECK ((state = 'in_transit') = (carried_by_uuid IS NOT NULL)
       AND (carried_by_uuid IS NULL) = (carried_by_name IS NULL));
