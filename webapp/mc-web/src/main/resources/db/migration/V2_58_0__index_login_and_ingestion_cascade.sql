-- MCO-353: index the login write and the re-ingestion delete cascade.
--
-- Renumbered three times while this branch was open: V2_54_0 collided with PR #396 (MCO-316),
-- V2_56_0 with MCO-401's farm-scale threshold, then V2_57_0 with PR #401 (MCO-412). This is the
-- git-level collision CLAUDE.md warns about — no amount of database branching prevents two
-- branches picking the same number, and with parallel worktrees in flight the free number moves
-- under you.
--
-- The third collision is the instructive one: origin/master was checked and V2_57_0 was genuinely
-- free there. It was taken by an *open PR* that had not merged yet. Checking master is not
-- sufficient — the number has to be free across open PR head refs too (MCO-408).
--
-- Purely additive: no column, constraint or row is touched, so this is safe to replay and safe to
-- roll back by dropping the indexes.
--
-- Two separate problems, one migration.

-- ---------------------------------------------------------------------------------------------
-- 1. The login write
-- ---------------------------------------------------------------------------------------------
-- UpdateLastSignInStep runs `UPDATE minecraft_profiles SET last_login = NOW() WHERE username = ?`
-- on every single sign-in, and V2_2_0 indexed only user_id and uuid.
--
-- Pre-emptive, NOT a fixed regression. The table holds 7 rows in a single page, so Postgres
-- correctly prefers a seq scan (1.8ms) and will keep ignoring this index until the table reaches
-- roughly a thousand rows. `enable_seqscan=off` confirms the index is usable when the planner
-- wants it. It costs ~8kB and no write amplification worth measuring — `username` is never
-- updated, so HOT updates on last_login are preserved. MCO-353's acceptance criterion asked for a
-- planner change here; that criterion cannot be met at this table size and should not be read as
-- outstanding work.
--
-- Deliberately NOT unique. Minecraft usernames are unique per account at any instant but are
-- reassignable over time, so a rename that frees a name for another player would make a unique
-- constraint reject the second player's profile row at exactly the wrong moment. The lookup wants
-- an index; it does not want a promise the game does not make.
CREATE INDEX IF NOT EXISTS idx_minecraft_profiles_username
    ON minecraft_profiles (username);

-- ---------------------------------------------------------------------------------------------
-- 2. The re-ingestion delete cascade
-- ---------------------------------------------------------------------------------------------
-- All four tables declare `resource_source_id ... REFERENCES resource_source ON DELETE CASCADE`
-- but V2_26_0 indexed them only on (version, item|tag). Postgres does not auto-index the
-- referencing side of a foreign key, so `DELETE FROM resource_source WHERE version = ?` in
-- StoreServerDataSteps seq-scans all four once per parent row deleted.
--
-- Measured on a production fork by dropping these four inside a rolled-back transaction, same
-- session and same warm buffers, so the comparison is the index and not cache state:
--
--     DELETE FROM resource_source WHERE version = '26.2.0'   (3,269 rows, the largest version)
--       with these indexes:      171 ms
--       without them:         30,294 ms
--
-- The mechanism is unambiguous: with no index on the referencing side, the RI cascade trigger
-- seq-scans 105,506 produced_item rows once per parent row deleted.
--
-- Note what the 30s figure means. STATEMENT_TIMEOUT_SECONDS in Database.kt is 30, so *without*
-- these indexes this statement misses the timeout by 0.3s. MCO-347 and MCO-353 are therefore
-- hard-coupled: dropping this migration, or rolling it back without also raising that timeout,
-- makes the nightly re-ingest fail with 57014 every night. Deploy order is already correct
-- (prod.yml migrates before the image ships) — this note exists so the coupling is not discovered
-- by an outage.
CREATE INDEX IF NOT EXISTS idx_resource_source_produced_item_source
    ON resource_source_produced_item (resource_source_id);
CREATE INDEX IF NOT EXISTS idx_resource_source_produced_tag_source
    ON resource_source_produced_tag (resource_source_id);
CREATE INDEX IF NOT EXISTS idx_resource_source_consumed_item_source
    ON resource_source_consumed_item (resource_source_id);
CREATE INDEX IF NOT EXISTS idx_resource_source_consumed_tag_source
    ON resource_source_consumed_tag (resource_source_id);

-- ---------------------------------------------------------------------------------------------
-- 3. The same pattern elsewhere
-- ---------------------------------------------------------------------------------------------
-- Unindexed foreign keys found alongside the two above. These are pre-emptive: each closes a
-- known seq-scan-on-parent-delete, but none has a measured query behind it the way section 2
-- does. Kept because an unindexed FK referencing side is a latent version of exactly the problem
-- section 2 fixes; revisit if index maintenance ever shows up in ingest timings.

-- minecraft_tag_item has (version, tag) but not (version, item).
--
-- Honest scope note: there is no item-first query today. The only read is ItemSourceGraphSteps'
-- `LEFT JOIN minecraft_tag_item ti ON ti.version = t.version AND ti.tag = t.tag`, which is
-- tag-first and already served by idx_tag_item_lookup, and the FK cascade is tag-side too. This
-- is 27k rows of index maintained on every ingest for a lookup shape the code does not yet
-- perform. Drop it if "which tags contain this item" has not appeared by the next ingest change.
CREATE INDEX IF NOT EXISTS idx_minecraft_tag_item_item
    ON minecraft_tag_item (version, item);

-- Primary key is (user_id, project_id), so project_id is not a leading column and deleting a
-- project cannot use it.
CREATE INDEX IF NOT EXISTS idx_user_project_view_preference_project
    ON user_project_view_preference (project_id);

-- Foreign keys whose referencing side is unindexed, so deleting the parent seq-scans the child.
CREATE INDEX IF NOT EXISTS idx_projects_project_idea_id
    ON projects (project_idea_id);
CREATE INDEX IF NOT EXISTS idx_idea_drafts_source_idea_id
    ON idea_drafts (source_idea_id);
CREATE INDEX IF NOT EXISTS idx_global_user_roles_granted_by
    ON global_user_roles (granted_by);
CREATE INDEX IF NOT EXISTS idx_device_code_user_id
    ON device_code (user_id);
CREATE INDEX IF NOT EXISTS idx_resource_gathering_item_id
    ON resource_gathering (item_id);
