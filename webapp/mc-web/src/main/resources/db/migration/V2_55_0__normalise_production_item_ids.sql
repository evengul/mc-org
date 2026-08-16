-- MCO-316: normalise legacy project_productions.item_id to namespaced item ids.
--
-- project_productions was written back when produced items were picked out of the Minecraft
-- language file, so its item_id holds a *translation key* — "block.minecraft.cobblestone".
-- Everything else in the schema uses the namespaced id — "minecraft:cobblestone" — including
-- minecraft_items, resource_gathering and project_demand. The two never join.
--
-- The consequence was silent and total: farm supply matched nothing. Not "matched the wrong
-- quantity" (MCO-316's premise) but zero rows, in both directions —
--
--   * GetFarmSupplyEdgesStep produced no roadmap edge for any real farm; and
--   * GetWorldFarmSuppliesStep fed translation keys into the planner as supply, where they
--     match no node in the item graph, so every operational farm supplied nothing to every
--     gathering plan.
--
-- On the dev world that is 104 rows across 23 projects, and 20-odd DONE farms contributing
-- nothing to a 500,000-item build plan.
--
-- The write path has since been fixed on its own: RecordExistingFarmPipeline resolves the
-- submitted id against the item catalog and stores item.id, so only pre-existing rows carry
-- the old shape and no new ones can appear. This is a one-time data repair.

UPDATE project_productions pp
SET item_id    = 'minecraft:' || regexp_replace(pp.item_id, '^(block|item)\.minecraft\.', ''),
    updated_at = CURRENT_TIMESTAMP
WHERE pp.item_id ~ '^(block|item)\.minecraft\.[a-z0-9_]+$'
  -- Stripping the prefix is not a safe mapping on its own: a translation key is not an item
  -- id, and for a good number of items the two simply differ (block.minecraft.grass is
  -- minecraft:short_grass). The danger is not the rewrite that lands nowhere — it is the one
  -- that lands on a real but *different* item. So each rewrite must satisfy both halves:
  --
  --   1. the id exists in the catalog *for the version this project's world plans against*, and
  --   2. the display name the lang file gave this row still matches the catalog's name for it.
  --
  -- Two independent sources agreeing on the same item is what makes the mapping evidence rather
  -- than a guess. Both halves are load-bearing. Existence alone passes block.minecraft.grass →
  -- minecraft:grass, a real id that has meant nothing since 1.20.3 (the block is short_grass
  -- now) and would leave a farm supplying an item no current plan can consume — which is the
  -- version scope. And an id can be current while the row means something else — which is the
  -- name check. Anything failing either half keeps its old value: no worse than today, and
  -- re-pickable in the farm's own UI. That is where rows with further dotted segments land
  -- ("item.minecraft.potion.effect.healing" — a healing potion is minecraft:potion plus a
  -- component, so it has no id of its own to normalise to).
  AND EXISTS (SELECT 1
              FROM minecraft_items mi
                       JOIN projects proj ON proj.id = pp.project_id
                       JOIN world w ON w.id = proj.world_id
              WHERE mi.item_id = 'minecraft:' || regexp_replace(pp.item_id, '^(block|item)\.minecraft\.', '')
                AND mi.version = w.version
                -- Catalog names carry a " (Block)" / " (Item)" disambiguator the lang name has not.
                AND lower(regexp_replace(mi.item_name, ' \((Block|Item)\)$', '')) = lower(pp.name))
  -- (project_id, item_id) is unique. If a project somehow holds both shapes of the same item,
  -- keep the row that is already correct rather than failing the migration.
  AND NOT EXISTS (SELECT 1
                  FROM project_productions other
                  WHERE other.project_id = pp.project_id
                    AND other.item_id = 'minecraft:' || regexp_replace(pp.item_id, '^(block|item)\.minecraft\.', ''));

-- Stored demand must be discarded. The supplied map is keyed by these very item ids and is one
-- of the fingerprinted inputs, so every plan derived before this migration was derived against
-- an empty supply set: chains ran past items a farm already covers, and the raw inputs of those
-- items are in project_demand as demand that does not exist.
--
-- The fingerprint alone does not rescue this. It is checked where a plan is *written* (the
-- project page), not where demand is *read* (the roadmap), and the roadmap only derives for
-- projects that have no rows at all — so a project with stale rows would keep serving them
-- until someone happened to open it. Clearing both tables puts every affected project back in
-- "uncovered", which the roadmap's fill-on-read then re-derives once, correctly.
DELETE FROM project_demand;
DELETE FROM project_demand_state;
