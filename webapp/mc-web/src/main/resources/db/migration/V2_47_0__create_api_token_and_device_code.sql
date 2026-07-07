-- Mod-facing API auth (MCO-236). Two tables back the token layer and the OAuth-style
-- device-code flow the Seam Companion mod uses to authenticate without a browser redirect.
--
-- api_token: long-lived, revocable bearer tokens. Only the SHA-256 hash of the opaque token is
-- stored; the raw value is shown to the client exactly once at mint time.
--
-- device_code: RFC-8628-style device authorization. The mod creates a (device_code, user_code)
-- pair, shows the short user_code to the player, who approves it in the browser (/link). The mod
-- polls until the code is approved, then exchanges it for an api_token.

CREATE TABLE api_token (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 hex of the opaque token; the raw token is never persisted.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE,
    -- NULL = never expires. When set, a token past this instant is rejected.
    expires_at TIMESTAMP WITH TIME ZONE,
    -- Set when the token is revoked; a non-NULL value fails auth immediately.
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_api_token_token_hash ON api_token(token_hash);
CREATE INDEX idx_api_token_user_id ON api_token(user_id);

CREATE TABLE device_code (
    id BIGSERIAL PRIMARY KEY,
    -- Opaque high-entropy code the mod polls with; never shown to the user.
    device_code VARCHAR(255) NOT NULL UNIQUE,
    -- Short human-typable code (e.g. ABCD-EFGH) the player enters on the /link page.
    user_code VARCHAR(32) NOT NULL UNIQUE,
    -- Bound to the approving user once they confirm on /link; NULL while pending.
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'denied', 'expired')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_seconds INTEGER NOT NULL DEFAULT 5,
    -- Poll-rate enforcement (RFC 8628 slow_down): last time this code was polled.
    last_polled_at TIMESTAMP WITH TIME ZONE,
    -- Ensures an approved code mints its api_token exactly once.
    token_issued BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_device_code_device_code ON device_code(device_code);
CREATE INDEX idx_device_code_user_code ON device_code(user_code);
