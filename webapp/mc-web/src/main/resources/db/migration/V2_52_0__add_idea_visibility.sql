-- MCO-291: ideas carry visibility — private designs vs the public hub.
--
-- "My designs" and "the idea bank" are one system with a flag, not two systems. New ideas are
-- private by default: the creative -> survival flow produces personal designs that are not
-- community contributions, and private-by-default also sidesteps attribution (Seam only hosts
-- schematic files that are the user's own).
--
-- Publishing to the hub is the privileged step, replacing the old gate on *creating* an idea.
--
-- Stored as text + CHECK rather than a boolean so the scope ladder can grow (UNLISTED is the
-- likely next rung). Deliberately NOT modelled here: DRAFT (drafts are their own table with their
-- own shape) and WORLD (a share target, not a scope — that needs an idea_shares relation).

ALTER TABLE ideas ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PRIVATE';

ALTER TABLE ideas ADD CONSTRAINT ideas_visibility_check
    CHECK (visibility IN ('PRIVATE', 'PUBLIC'));

-- Everything that already exists was created under the old model, where every idea was on the
-- hub. Keep those visible rather than silently hiding them from their audience.
UPDATE ideas SET visibility = 'PUBLIC';

-- The hub listing filters on this on every page load.
CREATE INDEX idx_ideas_visibility ON ideas (visibility);
