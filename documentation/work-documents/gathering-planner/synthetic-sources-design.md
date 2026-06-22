# Synthetic Sources & Ingredients — Design

Status: **design (2026-06-22)** — agreed direction with Even, pre-implementation.
Origin: the gathering-planner workflow review ([workflow-testing-notes.md](workflow-testing-notes.md));
notes 6 (white concrete), 8 (honey/bees), 5 (water as an indirect item), and 7.2
(TNT inline-alternatives) all need data the Mojang JSON doesn't give us cleanly.

## Goal

Let the engine plan items whose real acquisition isn't in the extracted recipe/loot/
trade JSON — game mechanics (place concrete powder by water), tool-based world
collection (fill a bucket from water), bee harvesting, the wither's nether star — and
recipe ingredients expressed as an inline "any of these" list (TNT's `[sand, red_sand]`).

These are **two distinct mechanisms**; keeping them separate avoids one tangled map:

- **A — synthetic obtain-sources:** "how is item X gotten." A hardcoded `ResourceSource`.
- **B — synthetic ingredient tags:** "this recipe slot accepts any of these." A tag.

Both are **extraction-time** changes and need **re-ingestion** to take effect (the graph
is rebuilt from stored `resource_source_*` rows; ledger-driven ingestion only re-runs a
version when its server-JAR SHA changes, so re-ingest is forced for existing versions).

---

## Background: how synthetic data already flows

- `ExtractResourceSources` = loot ∪ recipes ∪ trades → `List<ResourceSource>`.
  `ExtractLootTables.hardcodedLoot()` already appends one synthetic source
  (`nether_star ← wither`). Stored sources are indistinguishable from real ones to the
  graph builder, so adding a `ResourceSource` is the entire mechanism for A.
- Tags persist via `StoreMinecraftItemDataStep`, which pulls `MinecraftTag`s out of
  `ServerData.items` and writes `minecraft_tag` + `minecraft_tag_item` from `tag.content`.
  A tag only persists its members if it rides in `items` with `content` populated — the
  one real plumbing point for B.
- `SelectionScorer` scores a candidate by `source.sourceType.score`; `ActivityOrdering`
  (in `mc-engine/.../GatheringPlan.kt`) maps a `SourceType` to an `ActivityGroup`. A new
  `SourceType` therefore needs (1) a base score, (2) an `ActivityGroup` case, (3) entry in
  `SourceType.of(id)` so it round-trips through storage.

---

## Mechanism A — synthetic obtain-sources

### New SourceTypes

Concrete-in-water is **not** crafting and water-from-a-pool is **not** block-breaking, so
rather than mislabel them we add two distinct types (Even: "keep it even more distinct").
Honey fits an existing interaction type; the nether star fits ENTITY. So only two are new:

| New `SourceType` | id | score* | `ActivityGroup` | For |
|------------------|----|--------|-----------------|-----|
| `COLLECT` | `mcorg:collect` | 100 | `GATHER` | fill a bucket from a world fluid (water, lava) — free/abundant, should win outright |
| `GAME_MECHANIC` | `mcorg:game_mechanic` | 90 | `CRAFT` | deliberate in-world transform (concrete powder → concrete by water); future: mud, etc. |

\* **Scores require Even's sign-off** (scoring is the restricted area). Rationale: water
is effectively free so 100 (ties block-breaking, fine); concrete's only real alternative
is self-break (penalised) so 90 wins comfortably while still costing its powder chain.
`COLLECT`/`GAME_MECHANIC` are in none of RECIPE/LOOT/TRADE type-sets, so `isRecipe()` etc.
return false (no recipe-threshold/efficiency surprises). `ActivityGroup.GATHER`/`CRAFT`
reuse keeps the roadmap readable; a dedicated `COLLECT`/`BUILD` group is possible later
if the play-session labels want it.

Honey reuses existing types:
- `honeycomb ← shear a beehive` → `SHEARING` (90), no consumed item (shears reusable).
- `honey_bottle ← bottle a beehive` → `BLOCK_INTERACT` (90), consumes `glass_bottle` ×1.

### Tools are not materials

Water/lava are modelled as **free terminals** (no `requiredItems`). The bucket is a tool
the player needs, like an axe for chopping wood or shears for honeycomb — **not a consumed
material**, and reusable, so threading it through the quantity model would over-count
(N water ≠ N buckets). Tools are out of scope here. *(Future idea: a "required tools" hint
surfaced in the UI — axe/bucket/shears/silk-touch — separate from the quantity DAG.)*

### Registry

New `SyntheticSources` object in `mc-data` (`extract/`), returning `List<ResourceSource>`,
appended in `ExtractResourceSources`. The `nether_star ← wither` entry **moves here** out of
`ExtractLootTables.hardcodedLoot()` (which then goes away). Filename convention
`synthetic/<name>.json` so entries are identifiable and never collide with real loot files.

Entries:

| Item(s) | Source | Type | Requires | Produces |
|---------|--------|------|----------|----------|
| `nether_star` | `synthetic/wither.json` | ENTITY | — | 1 (migrated) |
| `honeycomb` | `synthetic/beehive_shear.json` | SHEARING | — | 3 |
| `honey_bottle` | `synthetic/beehive_bottle.json` | BLOCK_INTERACT | `glass_bottle` ×1 | 1 |
| `<color>_concrete` ×16 | `synthetic/<color>_concrete.json` | GAME_MECHANIC | `<color>_concrete_powder` ×1 | 1 |
| `water` | `synthetic/water.json` | COLLECT | — | 1 |
| `lava` | `synthetic/lava.json` | COLLECT | — | 1 |

The 16 concrete entries are generated by looping the dye-colour list, not hand-written.

### Effect on the notes

- Note 8 (honey): `honey_bottle` now has a BLOCK_INTERACT source (90) > chest (60), and the
  circular craft is no longer the only non-loot option → resolves to bee harvesting.
- Note 6 (concrete): `white_concrete` gets a GAME_MECHANIC source consuming concrete powder,
  instead of only self-break.
- Note 5 (water): `water` becomes a COLLECT terminal instead of BLOCKED.

No `SelectionScorer` logic change — only new base scores on the new types (sign-off item).

---

## Mechanism B — synthetic ingredient tags (inline alternatives)

The TNT case: a recipe slot is a JSON array `[minecraft:sand, minecraft:red_sand]` — a real
user choice, semantically an `OPEN_TAG`. There is no vanilla tag for exactly that pair
(`#minecraft:sand` also includes `suspicious_sand`), so we synthesise an anonymous tag.

### Parser change

In `ShapedRecipeParser` (key entry) and `ShapelessRecipeParser`/`CraftingValuesParser`
(ingredient slot), an ingredient that is a JSON array of ≥2 resolvable items becomes a
synthetic `MinecraftTag`:

- **id:** `#mcorg:choice/<sorted member local-names joined by '_'>` — deterministic, so the
  same alternative-set merges to one node across recipes and is stable across re-ingests.
  e.g. `#mcorg:choice/red_sand_sand`.
- **name:** human form, e.g. "Sand or Red Sand".
- **content:** the alternative `Item`s.

A single-element array is just that item (no tag); a tag reference stays a tag as today.
This also removes the existing latent bug where the shapeless parser silently takes the
first alternative.

### Persistence plumbing (the only non-trivial bit)

Synthetic tags created during recipe extraction must reach `ServerData.items` with their
`content`, so `StoreMinecraftItemDataStep` writes `minecraft_tag` + `minecraft_tag_item`.
Plan: recipe extraction surfaces the synthetic tags it created (alongside the sources), and
the extraction orchestrator (`ExtractMinecraftDataStep` / wherever `ServerData` is assembled)
folds them into `items`, deduped by id. `LoadResourceSourcesForVersionStep` then reconstructs
them with members exactly like vanilla tags.

### Engine

**No change.** A synthetic tag with members behaves like any tag ingredient: without a
`PlanOverrides.tagMember` choice it surfaces as `OPEN_TAG` (Needs Attention / Sharpen / the
drill), and the user resolves it via the existing `PlanOverride.TagMember`.

---

## Sequencing

**A first, then B** (Even). A is additive and immediately unblocks honey/concrete/water; B
carries the `ServerData.items` plumbing and the parser changes. Each ships + re-ingests
independently.

## Open sign-offs

1. **Base scores** for `COLLECT` (100) and `GAME_MECHANIC` (90) — restricted scoring area.
2. **ActivityGroup** mapping: `COLLECT`→`GATHER`, `GAME_MECHANIC`→`CRAFT` (vs. dedicated groups).

## Testing

- mc-data unit: `SyntheticSources` produces the expected sources (counts, types, requires);
  concrete loop covers all 16 colours; shaped/shapeless parser tests for the array→tag case
  (incl. the deterministic id and the single-element fallback).
- mc-engine unit: a constructed graph with a COLLECT terminal and a GAME_MECHANIC recipe
  selects/groups them as intended; honey_bottle prefers BLOCK_INTERACT over chest; an inline
  synthetic tag surfaces as OPEN_TAG.
- Re-ingest a worktree version and re-run the `unobtainable` diagnostic: water/honey/concrete
  drop off the list; spot-check TNT shows the sand/red_sand choice and concrete its powder.
