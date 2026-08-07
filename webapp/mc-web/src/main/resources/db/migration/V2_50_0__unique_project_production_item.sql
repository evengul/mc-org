-- MCO-297: POST /worlds/{w}/projects/{p}/productions is an upsert on (project_id, item_id),
-- which needs a unique constraint. Deduplicate first — idea imports could have written the
-- same item twice; keep the lowest id per pair.
DELETE FROM project_productions a
USING project_productions b
WHERE a.project_id = b.project_id
  AND a.item_id = b.item_id
  AND a.id > b.id;

ALTER TABLE project_productions
ADD CONSTRAINT uq_project_productions_project_item UNIQUE (project_id, item_id);
