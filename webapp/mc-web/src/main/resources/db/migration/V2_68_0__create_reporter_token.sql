-- Reporter tokens (MCO-531) — Shared Storage phase A.
--
-- The server half of the mod authenticates as ITSELF, not as a player. It is not a person, it has
-- no Minecraft account, and it must not hold a token that can do what a person can: a reporter
-- token sits in a plaintext config file on a machine whose operator is not necessarily the world
-- owner. It may read one world's container tags and write that world's container contents. That is
-- all — no other world's projects, no progress, no tasks.
--
-- WHY A SEPARATE TABLE, rather than a nullable world_id on api_token
-- -----------------------------------------------------------------
-- ApiBearerAuthPlugin resolves a bearer token against api_token and yields a USER ID, which every
-- player route then uses for its membership check. A reporter has no user. Storing one in api_token
-- with a null user_id would mean every existing route had to cope with "authenticated, but nobody"
-- — and a route that forgot would fail OPEN, which is the one failure mode this token type exists
-- to avoid.
--
-- Kept apart, the player-token plugin simply cannot resolve a reporter token: the hash is not in
-- the table it looks in. The reporter is rejected from every player route by construction rather
-- than by a check somebody has to remember to write.

CREATE TABLE reporter_token (
    id BIGSERIAL PRIMARY KEY,
    -- The single world this token speaks for. A server hosting several Seam worlds needs several
    -- tokens; binding a token to a set is deferred until that actually happens.
    world_id INTEGER NOT NULL REFERENCES world(id) ON DELETE CASCADE,
    -- SHA-256 hex, exactly as api_token stores it. The raw token is shown once at mint time.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    -- Operator-supplied label, so several servers on one world stay tellable apart.
    name VARCHAR(255),
    -- Who minted it, for the world-settings list. A deleted user leaves the token working.
    created_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Stamped by the contents push (MCO-532), which is why there is no separate heartbeat
    -- endpoint: a reporter with nothing to report pushes an empty payload, and that is the
    -- heartbeat. NULL here is the "never connected" state world settings must show loudly.
    last_used_at TIMESTAMP WITH TIME ZONE,
    -- Which reporter build last used this token. Also written by the push (MCO-532).
    reporter_version VARCHAR(64),
    -- Set when revoked from world settings; a non-NULL value fails auth immediately.
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- No index on token_hash: the UNIQUE above already builds a unique btree, and a second one is pure
-- write amplification. (api_token's migration has both; that is a mistake worth not copying.)
CREATE INDEX idx_reporter_token_world_id ON reporter_token(world_id);
