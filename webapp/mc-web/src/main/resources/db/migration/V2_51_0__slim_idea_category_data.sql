-- Migration V2_51_0: Slim the idea category schema (MCO-204)
--
-- The per-category schemas dropped their long tail of speculative typed fields (~20 FARM
-- fields, 12 STORAGE subcategories, 8 SLIMESTONE subcategories, TNT duper/compressor blocks)
-- in favour of a free-form `specs` label -> value block. Anything still stored under a
-- retired key would fail schema lookup on render and be silently skipped.
--
-- There is no production idea data worth preserving, so this clears category data outright
-- rather than attempting to fold retired keys into `specs`.

UPDATE ideas
SET category_data = '{}'::jsonb
WHERE category_data IS NOT NULL
  AND category_data <> '{}'::jsonb;

-- In-progress drafts carry the same shape under data->'categoryData'.
UPDATE idea_drafts
SET data = data - 'categoryData'
WHERE data ? 'categoryData';
