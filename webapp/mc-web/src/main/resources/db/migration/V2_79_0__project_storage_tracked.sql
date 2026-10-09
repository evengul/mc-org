-- Storage-tracked: a project whose counts follow its tagged chests (MCO-540).
--
-- Off by default. While it is off, `collected` is the human's number and the chests are evidence
-- shown beside it (the drift chip, MCO-539). Turned on, every recompute of the project's
-- measurement also writes `collected := measured`, and every door that writes a count by hand
-- refuses — a counter that a sweep overwrites every 30 seconds is worse than either design alone.
--
-- A column on projects rather than a table of its own: it is one fact about one project, read on
-- every page that renders a counter, and it has no history worth keeping. Turning it off keeps the
-- last followed values as ordinary counts, so nothing needs to be remembered from before it was on.
ALTER TABLE projects
    ADD COLUMN storage_tracked BOOLEAN NOT NULL DEFAULT false;
