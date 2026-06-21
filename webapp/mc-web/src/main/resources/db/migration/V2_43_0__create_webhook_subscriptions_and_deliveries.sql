-- Webhook fan-out (MCO-229): delivery layer between the in-process event bus (MCO-228) and
-- external HTTP endpoints (the seam-discord bot is the first consumer; the design is
-- endpoint-agnostic). Two tables: subscriptions (who wants which events, where) and a
-- transactional outbox (one row per (subscription, event), driven by a background poller).

CREATE TABLE webhook_subscriptions (
    id SERIAL PRIMARY KEY,
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    callback_url TEXT NOT NULL,
    -- Shared secret used to sign each delivery's raw body (HMAC-SHA256 -> X-Seam-Signature).
    secret TEXT NOT NULL,
    -- JSONB array of event-type strings to deliver, or ["*"] for all. Producers are unaware
    -- of subscribers; matching happens here at fan-out time.
    event_filter JSONB NOT NULL DEFAULT '["*"]',
    -- Consumer-specific routing data (e.g. the Discord channel id). Opaque to fan-out.
    metadata JSONB NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Consecutive failed deliveries; the subscription auto-deactivates once this hits the
    -- health threshold (see WebhookDeliveryPoller). Reset to 0 on any successful delivery.
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_subscriptions_world_active ON webhook_subscriptions(world_id, active);

CREATE TABLE webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    subscription_id INTEGER NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    -- The serialized event envelope ({event_type, world_id, timestamp, actor, data}).
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    -- The poller picks up PENDING rows whose next_attempt_at is due; retry backoff bumps it.
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

-- Drives the poller's "due work" scan: pending rows ordered by when they became due.
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries(status, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_subscription ON webhook_deliveries(subscription_id);
