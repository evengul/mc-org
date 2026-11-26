-- A box in transit may have no carrier on record: the carrier deleted their Seam account.
--
-- DeleteAccountStep clears carried_by_uuid and carried_by_name on every box the deleted account is
-- carrying, since a name and a UUID are what the privacy page promises to remove. The box stays
-- 'in_transit' so its contents keep counting for the world's other members: 'missing' would clear
-- them and drop the count to zero while the stock is still on its way to the build. The next push
-- that names the box sets a carrier again, from what the server sees then.
--
-- So the rule is now: a carrier only while in transit, and always both halves of one. The reporter
-- still has to name a carrier for every box it reports in transit (ApiRoutes).

ALTER TABLE container_tags DROP CONSTRAINT container_tags_carried_by_check;
ALTER TABLE container_tags ADD CONSTRAINT container_tags_carried_by_check
    CHECK ((state = 'in_transit' OR carried_by_uuid IS NULL)
       AND (carried_by_uuid IS NULL) = (carried_by_name IS NULL));
