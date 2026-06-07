-- Single source of truth for "has this Minecraft version been ingested, and from which server.jar".
-- Replaces the 4-table EXISTS proxy in AllFileMigrationsCompletedStep (GetServerFilesPipeline.kt).
CREATE TABLE minecraft_version_ingestion (
    version        VARCHAR(50) PRIMARY KEY REFERENCES minecraft_version(version),
    server_jar_sha TEXT,           -- SHA1 from Mojang's version meta; NULL when unknown
    server_jar_url TEXT,
    status         TEXT NOT NULL CHECK (status IN ('pending', 'in_progress', 'completed', 'failed')),
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    last_error     TEXT,
    attempt_count  INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_minecraft_version_ingestion_status ON minecraft_version_ingestion(status);

-- Backfill one 'completed' row per version where the legacy EXISTS check currently passes.
-- server_jar_sha stays NULL: we don't know the SHA retroactively. The freshness check (MCO-168)
-- treats NULL as "data exists, SHA unknown" rather than "needs re-ingestion".
INSERT INTO minecraft_version_ingestion (version, status, completed_at, attempt_count)
SELECT mv.version, 'completed', now(), 1
FROM minecraft_version mv
WHERE EXISTS(SELECT 1 FROM minecraft_items mi WHERE mi.version = mv.version)
  AND EXISTS(SELECT 1 FROM minecraft_tag tags WHERE tags.version = mv.version)
  AND EXISTS(SELECT 1 FROM minecraft_tag_item tag_items WHERE tag_items.version = mv.version)
  AND EXISTS(SELECT 1 FROM resource_source rs WHERE rs.version = mv.version);
