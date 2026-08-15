-- Drop users.email (MCO-339).
--
-- The column never held an email. Every row was created with the synthetic placeholder
-- '<minecraft-uuid>@minecraft.temp' by CreateUserIfNotExistsStep, because the column was
-- NOT NULL and the Microsoft/Xbox sign-in flow only ever yields a UUID and a username.
--
-- Verified before writing this migration: all 7 rows in production matched '%@minecraft.temp',
-- and zero did not. No real address is being destroyed.
--
-- Not backfilled with a real address instead, deliberately. Nothing in the app consumes an
-- email — there is no email notification path, and the only reader was an admin table column
-- that rendered the placeholder. Collecting an address we do not use would turn a pseudonymous
-- dataset into personal data, which is the opposite of what Epic B is for. If a genuine use
-- appears (account recovery, notifications), fetch it then: `openid` is already in the OAuth
-- scope, so it is an `email` scope plus an id_token claim away.
--
-- The users row now carries no attributes of its own; identity lives on minecraft_profiles.
-- Inserts use `INSERT INTO users DEFAULT VALUES`.

ALTER TABLE users DROP COLUMN email;
