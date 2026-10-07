-- MCO-424: one active webhook subscription per Discord channel per world.
--
-- Connecting a channel was a plain INSERT, so connecting it again (which is what
-- webhook-contract.md tells a user to do to pick up a narrowed event filter) left two active
-- subscriptions on one channel, and every event was posted to it twice. Connecting is now an
-- upsert on this index (CreateWebhookSubscriptionStep).
--
-- Keyed on the channel id, not callback_url: the callback URL carries `?compact=1`, so the same
-- channel connected once compact and once full had two different URLs. Partial on `active`, so a
-- subscription that auto-deactivated does not block reconnecting the channel. Rows without a
-- discord_channel_id (other consumers, via the admin endpoint) key on NULL and are unconstrained.

-- Existing duplicates: keep the newest, which carries the filter the user last connected with, and
-- deactivate the rest rather than deleting them, so their delivery history stays.
UPDATE webhook_subscriptions s
SET active = false,
    updated_at = CURRENT_TIMESTAMP
WHERE s.active = true
  AND s.metadata ? 'discord_channel_id'
  AND EXISTS (
      SELECT 1
      FROM webhook_subscriptions newer
      WHERE newer.active = true
        AND newer.world_id = s.world_id
        AND newer.metadata ->> 'discord_channel_id' = s.metadata ->> 'discord_channel_id'
        AND newer.id > s.id
  );

CREATE UNIQUE INDEX uq_webhook_subscriptions_world_discord_channel
    ON webhook_subscriptions (world_id, (metadata ->> 'discord_channel_id'))
    WHERE active = true;
