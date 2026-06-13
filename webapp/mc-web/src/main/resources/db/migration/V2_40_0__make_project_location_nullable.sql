-- Project location is now optional.
--
-- Previously every project was forced to (0, 0, 0, OVERWORLD) on creation and
-- there was no edit path, so "this build is at spawn" was indistinguishable
-- from "nobody set a location". Make the columns nullable and clear the fake
-- default so an unset location reads as NULL.

ALTER TABLE projects
    ALTER COLUMN location_x DROP NOT NULL,
    ALTER COLUMN location_y DROP NOT NULL,
    ALTER COLUMN location_z DROP NOT NULL,
    ALTER COLUMN location_dimension DROP NOT NULL;

-- Only clear rows that still carry the synthetic default; preserve anything
-- that happens to differ (defensive — no edit path has existed, so in practice
-- this clears every existing row).
UPDATE projects
SET location_x = NULL,
    location_y = NULL,
    location_z = NULL,
    location_dimension = NULL
WHERE location_x = 0
  AND location_y = 0
  AND location_z = 0
  AND location_dimension = 'OVERWORLD';
