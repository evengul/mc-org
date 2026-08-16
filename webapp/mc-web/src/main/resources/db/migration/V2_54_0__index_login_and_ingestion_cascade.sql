-- MCO-353: index the login write and the re-ingestion delete cascade.
--
-- Purely additive: no column, constraint or row is touched, so this is safe to replay and safe to
-- roll back by dropping the indexes.
--
-- Two separate problems, one migration.

-- ---------------------------------------------------------------------------------------------
-- 1. The login write
-- ---------------------------------------------------------------------------------------------
-- UpdateLastSignInStep runs `UPDATE minecraft_profiles SET last_login = NOW() WHERE username = ?`
-- on every single sign-in, and V2_2_0 indexed only user_id and uuid. Verified as a Seq Scan.
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
-- At the sizes measured on a production fork — 76k resource_source rows against 105k
-- produced_item and 54k consumed_item rows — that is the expensive half of every re-ingest.
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
-- Unindexed lookups and unindexed foreign keys found alongside the two above.

-- minecraft_tag_item has (version, tag) but not (version, item); resolving "which tags contain
-- this item" is an item-first lookup over 27k rows.
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
