-- MCO-357: let the webhook poller claim a row before delivering it.
--
-- findDueDeliveries selected PENDING rows and the row was only mutated *after* the HTTP POST
-- returned, so two pollers running the same scan would both see the same rows and both deliver
-- them. Today fly.toml runs a single machine, but auto_start_machines = true, and any future
-- scale-out would turn every webhook into a duplicate — silently, and only in production.
--
-- IN_FLIGHT is the claim. A claiming UPDATE flips PENDING -> IN_FLIGHT and returns the rows in one
-- statement, so a second poller's `status = 'PENDING'` filter no longer matches them.
ALTER TABLE webhook_deliveries
    DROP CONSTRAINT IF EXISTS webhook_deliveries_status_check;

ALTER TABLE webhook_deliveries
    ADD CONSTRAINT webhook_deliveries_status_check
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'FAILED'));

-- When the claim was taken. Needed because a process that dies mid-delivery leaves its rows
-- IN_FLIGHT with nobody coming back for them; the poller reclaims a stale claim rather than
-- treating it as permanently owned, which would silently drop the delivery.
ALTER TABLE webhook_deliveries
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP WITH TIME ZONE;

-- The existing idx_webhook_deliveries_due is on (status, next_attempt_at) and still serves the
-- PENDING scan. This one serves the reclaim scan, which looks at IN_FLIGHT rows by claim age.
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_claimed
    ON webhook_deliveries (status, claimed_at);
