# Gathering Planner — Workflow Testing Notes & Scoring Triage

Captured 2026-06-22 from a live walkthrough of the import → plan → drill workflow
(worktree `engine-score-tuning`, against the worktree's Neon branch forked from
`master`, real ingested data). Source of the "strange prioritisations" Even noticed.

The base **source-type scores are sane** (`BLOCK`/`ENTITY` 100 > `CRAFTING` 95 >
villager trades 70 > wandering 65 > `CHEST` 60 — see `ResourceSource.SourceType`).
Hand-computing the scorer factors for several notes says the *good* path should
already win (sand mining 100 vs wandering 65; white-dye craft ~75 vs wandering 65;
cobblestone mining 100 vs stonecutting 90). Since the live result is the opposite,
the discrepancy is most likely in the **extracted graph data** (missing/odd sources,
quantities, yields) — not the scorer constants. **Do not tune weights blind**; dump
real factor breakdowns first. Any actual `SelectionScorer` weight/ranking change is a
restricted-area edit needing Even's sign-off before commit.

## The 12 notes

| # | Observation | Bucket | Root-cause hypothesis |
|---|-------------|--------|-----------------------|
| 1 | Empty-state projects list "create" button differs from the populated-state one; want to create a project **from a schematic** directly, not create-empty-then-upload | UI (mc-web) | Divergent empty/populated templates; add schematic entry to the empty state |
| 2 | An empty, pending project takes up very little space in the list — almost just looks like a button | UI | Project-list row styling for empty/pending projects |
| 3 | Uploading a large schematic shows no loading animation; nothing seems to happen until a manual refresh, which then shows the Needs-attention/Gather/etc. sections | UI / async | Missing `hx-indicator`; the upload/parse may be long or async — needs progress feedback |
| 4 | The required-resources list still has a "source" field that's no longer needed | UI | Template cleanup; related to **MCO-226**, confirm scope |
| 5 | 3 items blocked "no feasible source" on `/worlds/11/projects/38`: Piston Head (understood), **Birch Wall Sign** and **Dead Horn Coral Wall Fan** should be craftable / silk-touch gatherable. Also a model gap: things not gathered as items — Piston Head ⇐ Piston, Water ⇐ water bucket or ice block | Data / model | Wall/placed-block variants need mapping to their item form; "indirect" items need a representation |
| 6 | White Concrete has a special creation: craft concrete powder + place next to water. Should we model gameplay mechanics as "special recipes" not in the JSON? | Synthetic source | Mechanic absent from Mojang JSON |
| 7a | TNT chain: Gunpowder picked from **wandering trader**, should prioritise **entity drop** (creeper) | Data / scoring | entity 100 > wandering 65 ⇒ drop should already win; entity source likely missing or odd-yield in data |
| 7b | TNT chain: emeralds sourced as **block of emerald** loot then unpacked, should prioritise **villager trade** | Scoring | Storage-block unpack: uncapped 9× efficiency bonus (+160) beats trade, then the block is sourced from chest loot. Same family as #10 |
| 7.2 | The **sand** branch of the TNT recipe is missing entirely from the drill-in page (should show even when entering from Block of Emerald) | Engine / UI | `perTarget` expansion or DrillView depth-cap dropping the branch |
| 8 | Honey Bottle selects **chest loot**; best is **farming from bees** (mechanic may not be in our files — may need to add it, like water/concrete) | Data (missing) + synthetic source | Bee-harvest mechanic absent ⇒ chest is the only candidate and wins by default |
| 9 | Clicking "back to plan" from a drill should restore the **scroll position** the user drilled in from | UI / HTMX | No scroll restoration across the swap |
| 10 | Detector Rail requires iron, which selects **block of iron from chest loot** (unpack); should **smelt raw iron** from an iron ore block | Scoring | Storage-block unpack efficiency bug (same as #7b) |
| 11 | **Sand** and **white dye** prefer wandering trader; should prefer breaking block / crafting from some source | Data | Per factor math the good path should win ⇒ likely a missing block/recipe source in extracted data |
| 12 | **Cobblestone** prefers stonecutting from stone; should prefer breaking the block | Data | Missing stone→cobblestone drop edge, or self-block-loot penalty on placed cobblestone |

Even's summary: "mostly the low-level things, and mostly trades or special cases that
seem off."

## Workstreams

1. **Scoring/data diagnostics** — notes 7, 10, 11, 12, and 5's "why blocked".
   First action: a **read-only score-dump harness** — for a Minecraft version + item
   list, print every candidate source with its full factor breakdown (base,
   efficiency, supplied, recipe-threshold, self-block-loot, low-yield, requirement,
   depth) against the real ingested graph. Converts every note into an audit-style
   factor table; prerequisite for any weight change.
2. **Synthetic sources / indirect items** — notes 5 (water, piston head, wall
   variants), 6 (concrete), 8 (honey/bees). A mc-data / graph-build layer for
   mechanics absent from Mojang JSON. Own design effort.
3. **UI polish** — notes 1, 2, 3, 4, 9. Independent of the engine; parallelisable.

## Confirmed findings (score-dump diagnostic, version 26.2.0, world 11)

Diagnostic: `mvn -pl mc-web exec:java@score-diagnostics -Dexec.args="world=<id> demand=<n> <items…>"`
(read-only; `ScoreDiagnostics` in mc-engine + `cli/ScoreDiagnostics` in mc-web; prints the
`SelectionScorer` factor breakdown per candidate in `PlanSelector` rank order). Graph: 3237
sources, 1441 items.

### Headline: one factor — uncapped `efficiencyBonus` — drives 4 of the notes

`efficiencyBonus = ((produced/totalInput) − 1) · 20`, floored at 0, **never capped above**.
It misfires in two shapes:

- **On trades** (output-per-emerald is not an effort signal — emeralds are currency):
  - Sand (11): wandering trader **190** = base 65 **+ eff 140** (8× sand/emerald) ≫ mine 100.
  - White dye (11): wandering trader **90** = base 65 **+ eff 40** > craft-from-bone_meal 80.
  - Gunpowder (7a): wandering trader **110** = base 65 **+ eff 60** > creeper drop 100.
- **On storage-block unpack** (1 block → 9 items = ratio 9 = +160), block then sourced from chest:
  - Emerald (7b): craft-from-emerald_block **240** = base 95 **+ eff 160** ≫ mine ore 100, trades 55–85.
  - Iron ingot (10): craft-from-iron_block **240** = base 95 **+ eff 160** ≫ smelt raw iron 70.
  - Structural cycle-rejection misses these because the block is *independently* loot-obtainable
    (not circular), so the +160 branch survives.

### `recipeThresholdBonus` too aggressive — note 12

Cobblestone at demand ≥100: stonecutting stone→cobblestone gets **+50** → **125**, flipping above
mining stone (100). The bonus rewards a raw→raw conversion that saves no effort. (At demand 64
the scorer is correct: block-break 100 > stonecutting 75.)

### Data bugs (mc-data extraction / schematic import — NOT scoring)

- **TNT recipe is missing its sand ingredient** (7.2): the graph lists TNT `requires: gunpowder`
  only (should be 5 gunpowder + 4 sand). That's why sand never appears in the TNT drill — the
  edge isn't in the graph. Investigate the recipe parser in mc-data.
- **Wall / technical block-state ids have no item node** (5): `birch_wall_sign`,
  `dead_horn_coral_wall_fan`, `piston_head` are NOT IN GRAPH — the schematic import emits
  placed-block-state ids that have no item form. Map to the item form at import
  (`birch_sign`, `dead_horn_coral_fan`; `piston_head` ← `piston`) or drop technical blocks.

### Missing mechanics (synthetic sources — design work)

- **honey_bottle** (8): its only non-loot source is a *circular* craft (honey_block ← 4
  honey_bottle), so selection falls back to chest loot. No bee-harvest mechanic exists. The
  diagnostic's ▶ shows the craft (the scorer ignores feasibility); the planner rejects it.
- **white_concrete** (6): only candidate is self-break (base 100); the powder + water mechanic
  is absent.

### No action (confirmed correct)

- Gravel: mine 100 > bartering 80 — correct.
- Low-yield penalty working as designed (husk/zombie iron −40, sparse chests scaled down).
- Minor: `infested_cobblestone` ties stone (both 100) as a cobblestone source and wins the
  key tiebreak — a silly suggestion, worth a later data/tiebreak look.

### Proposed scoring fixes (RESTRICTED — need Even's sign-off before any edit)

- **R1 (low risk):** `efficiencyBonus` does not apply to trade source types. Fixes sand,
  white_dye, gunpowder. Clearly correct — trades aren't recipes.
  **DONE (2026-06-22, signed off by Even).** `SelectionScorer.efficiencyBonus` returns 0 for
  `isTrade()` sources. Verified: sand → mine 100 (trader 50), white_dye → craft 80 (trader 50),
  gunpowder → creeper 100 (trader 50). mc-engine tests 102 pass / 0 fail / 1 known skip.
  Not yet committed.
- **R2 (storage-block unpack):** cap `efficiencyBonus`, and/or penalize "unpack a storage block
  that must itself be gathered/looted". Fixes emerald, iron. Needs a design choice.
  **DONE (2026-06-22, signed off by Even).** Neither cap nor penalty: `efficiencyBonus` returns 0
  for a *reciprocal unpack* — a recipe whose single ingredient is, transitively, crafted from the
  output (`iron_ingot ← iron_block ← iron_ingot`; also the layered `copper_ingot ←
  waxed_copper_block ← copper_block ← copper_ingot`). Data showed every storage metal/gem has a
  mine/entity source at 100 above the zeroed unpack (80), so no penalty is needed; nuggets keep
  unpacking (80 > chest 60) because there is no penalty dragging them under loot. Verified across
  the family: emerald/diamond/coal → mine 100; iron/gold/copper → entity 100; iron_nugget →
  unpack 80; oak_planks 145 / stick 105 keep their efficiency (not reciprocal). mc-engine tests
  105 pass / 0 fail / 1 known skip. Not yet committed.
  *Parked (separate, needs farms/ideas):* entity base score (100) means iron/gold/copper resolve
  to golem/piglin/drowned rather than smelting at low demand — defensible (farmable), and the
  bulk threshold restores smelting at demand ≥100. Not an R2 regression.
- **R3 (recipe threshold):** withhold the bulk bonus when a raw-gather (BLOCK) source exists for
  the same item at comparable base. Fixes cobblestone.
  **DONE (2026-06-22, signed off by Even).** `recipeThresholdBonus` returns 0 when
  `hasMineableSource(item)` — the item drops from breaking a *different* block (self-block loot
  excluded). Verified: cobblestone @1000 → stonecutting 75 (no thr), mine 100 wins; iron_ingot
  @1000 still gets thr +50 on smelting (not mineable as an ingot), so no over-correction.
  mc-engine tests 102 pass / 0 fail / 1 known skip. Not yet committed.

## Status

- [x] 1. Score-dump diagnostic harness — **done** (`exec:java@score-diagnostics`)
- [x] 1b. R1 (no efficiency on trades) — done. R3 (threshold withheld when mineable) — done.
- [x] 1c. R2 (no efficiency on reciprocal/transitive storage-block unpacks) — done.
- [ ] 2. Data bugs: TNT-sand extraction, wall/technical-block item mapping
- [ ] 3. Synthetic sources / indirect items design (honey/bees, concrete, water, piston head)
- [ ] 4. UI polish batch (notes 1, 2, 3, 4, 9)
