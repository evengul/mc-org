-- Remove every other person's data from a fork of the production database.
--
-- A wt/* (worktree-db.sh) or dev/pr-<N> (dev.yml) Neon branch is a full copy of production, and it
-- outlives whatever happens in production after it was made: an account deleted there lives on in
-- every fork taken before. The privacy page says deleted data is gone once the restore window
-- passes, which is only true if forks hold nothing that belongs to anyone else. So a fork keeps
-- Minecraft's game data (items, recipes, loot, trades: nobody's personal data) and the owner's own
-- account, worlds and ideas. Everything else goes before anyone uses the fork.
--
--   psql "$URL" -v ON_ERROR_STOP=1 -v keep_user_id=1 -v demo_uuid="$DEMO_USER-uuid" -f scrub-fork.sql
--
-- keep_user_id is Seam's owner (production user 1). demo_uuid is the worktree's seeded sign-in user,
-- kept so that a re-run does not delete it and the ideas worktree-db.sh handed to it; pass an empty
-- string where there is none. Idempotent: a second run deletes nothing.
--
-- Runs after the migrations (the carrier rule below needs V2_81_0) and before the demo user is
-- seeded. One transaction, so a failure leaves the fork as it was and the caller reports it.

BEGIN;

CREATE TEMP TABLE keep_users ON COMMIT DROP AS
SELECT u.id FROM users u
WHERE u.id = :keep_user_id
   OR u.id IN (SELECT p.user_id FROM minecraft_profiles p WHERE p.uuid = :'demo_uuid');

-- Other people's worlds: the owner is not in it, and someone else is, or nobody is (left behind by
-- an account deleted before DeleteAccountStep removed such worlds). The owner's membership decides
-- rather than the demo user's, which is seeded into every world and would keep them all on a
-- re-run. A world with only the demo user in it is one the worktree made itself, a fixture, and
-- survives a re-run.
DELETE FROM world w
WHERE NOT EXISTS (
    SELECT 1 FROM world_members m WHERE m.world_id = w.id AND m.user_id = :keep_user_id
)
  AND (
    EXISTS (
        SELECT 1 FROM world_members m
        WHERE m.world_id = w.id AND m.user_id NOT IN (SELECT id FROM keep_users)
    )
    OR NOT EXISTS (SELECT 1 FROM world_members m WHERE m.world_id = w.id)
  );

-- Everyone else's account. The foreign keys onto users take their profile, memberships, ideas,
-- comments, drafts, invitations, notifications and mod tokens with them (DeleteAccountIT).
DELETE FROM users WHERE id NOT IN (SELECT id FROM keep_users);

-- Kept, but they name other people, and nothing points them at a user row.
--   A carrier is any player on the server, Seam account or not: forget all but the owner, as
--   DeleteAccountStep does for a deleted account. The box stays in transit and keeps counting.
UPDATE container_tags ct
SET carried_by_uuid = NULL, carried_by_name = NULL
WHERE ct.carried_by_uuid IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM minecraft_profiles p
      WHERE p.user_id = :keep_user_id
        AND replace(lower(p.uuid), '-', '') = replace(ct.carried_by_uuid::text, '-', '')
  );
--   Notification text quotes other people ("X invited you"). Both deletes also take what the
--   worktree made itself since the last run, which is the price of not telling the two apart.
DELETE FROM notifications;
--   Each delivery's payload carries the acting player's name. A fork never delivers them
--   (MCO-592), so they are nothing but copies.
DELETE FROM webhook_deliveries;

COMMIT;

-- What is left, for the caller's log.
SELECT (SELECT count(*) FROM users)  AS users,
       (SELECT count(*) FROM world)  AS worlds,
       (SELECT count(*) FROM ideas)  AS ideas;
