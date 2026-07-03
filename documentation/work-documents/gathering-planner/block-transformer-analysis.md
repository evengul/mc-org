# `minecraft:block_transformer` — extraction analysis

Status: **analysis (2026-07-03)** — snapshot only. Decision: **do it after 26.3 releases**
(autumn), not off snapshots. This document captures findings so the work can start the
day the full release lands.

Origin: 26.3 Snapshot 2 (2026-06-30) added the `minecraft:block_transformer` data
component. It looked directly relevant to the synthetic in-world-transform sources we
just shipped ([synthetic-sources-design.md](synthetic-sources-design.md)), so we
investigated whether — and how — it can feed the graph.

## TL;DR

- `block_transformer` **is** the data-driven form of our `IN_WORLD_TRANSFORM`
  (`mcorg:in_world_transform`) MechanicType: item-on-block interaction turns one block
  into another, optionally dropping loot.
- It is **not present in any datapack JSON inside the server JAR** — it exists only as a
  code-defined default item component. The only way to extract it is to run Mojang's
  **data generator** (`--reports`) and parse the per-item component reports.
- The high-value payload is **log/wood stripping** (`oak_log → stripped_oak_log`, ×25),
  which today resolves *incorrectly* (circular self-break). It plugs into the existing
  `isConstructive()` / self-block-penalty machinery with **zero scorer change**.
- Cost is an **ingestion-architecture change**: `GetServerFilesPipeline` currently only
  unzips JSON from the JAR; consuming this component means it must also run the data
  generator (JDK-version-locked, ~1 min per JAR) and parse a new report tree. **Agreed
  worth it post-26.3** — the component will almost certainly be expanded across the
  remaining snapshots before the autumn release.

---

## 1. What we were looking for

The question was narrow and decisive: **is `block_transformer` reachable by our
extraction pipeline, and if so in what shape?** Concretely:

- Does the component appear in files we already pull out of the server JAR
  (`data/minecraft/**` — recipes, loot tables, tags), or only somewhere we don't read?
- If we can get it, is the input-block → output-block mapping parseable into
  `ResourceSource` rows (requiredItems / producedItems), the way recipes and loot are?
- Which items/blocks does it cover, and how much of it is genuinely useful for planning
  vs. noise?

That determines whether this is "a new parser on the existing extraction" or "a new
capability in the ingestion pipeline."

## 2. How we went about finding it

Server JAR analysed: `26.3-snapshot-2`
(`https://piston-data.mojang.com/v1/objects/a6e2ae24c95dc838456927661a82434d26936d2f/server.jar`).

1. **Downloaded and unzipped the JAR.** Modern server JARs are a *bootstrap*: the real
   server lives at `META-INF/versions/26.3-snapshot-2/server-26.3-snapshot-2.jar`
   (found via `META-INF/versions.list`). Extracted that bundled jar.
2. **Grepped every `data/**.json` in the bundled jar for `block_transformer`.**
   Zero hits. Grepped the raw bytecode: **exactly one hit** — the component-id string
   constant in a class file. Conclusion: it is a *code-defined default component*, not a
   datapack resource. Also confirmed the jar ships **no** `reports/` or `items.json`.
3. **Ran the data generator** to see whether the component surfaces in generated reports:
   `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports`.
   - First attempt failed: `UnsupportedClassVersionError` — class file version **69.0**,
     i.e. the jar requires **JDK 25**. (Local default was 21.) Pulled a Temurin 25 build
     and re-ran.
   - Output landed under `generated/reports/minecraft/components/item/<item>.json` — one
     file per item, each a `{ "components": { … } }` object. (Note: the old single
     `reports/items.json` is gone; components are now split per item.)
4. **Parsed the reports** for every item carrying the component and extracted the
   input→output pairs, `loot`, `sound`, and `consume_on_use` fields to gauge shape and
   value.

## 3. What we found

### Carriers

**21 items, all tools** — axes, hoes, shovels across all six material tiers
(`wooden`/`stone`/`copper`/`iron`/`golden`/`diamond`/`netherite`). **Honeycomb does
*not* carry it yet** — copper *waxing* (`wax_on`) is still handled by legacy interaction
code, not migrated to the component. (This matters for cycles — see below.)

### JSON shape (parseable)

Each component is a **list of rule objects**. The mapping we need is:

- **input block** = `block_state_provider.rules[].if_true.blocks` (a block id or list,
  predicate type `minecraft:matching_blocks`)
- **output block** = the `then` provider's `…state.Name`

Real example (`iron_axe`, first rule):

```json
{
  "block_state_provider": {
    "type": "minecraft:rule_based_state_provider",
    "rules": [{
      "if_true": { "type": "minecraft:matching_blocks", "blocks": "minecraft:oak_log" },
      "then": {
        "type": "minecraft:copy_properties_provider",
        "source_block_state_provider": {
          "type": "minecraft:simple_state_provider",
          "state": { "Name": "minecraft:stripped_oak_log", "Properties": { "axis": "y" } }
        }
      }
    }]
  },
  "item_damage_per_use": 1,
  "sound": "minecraft:item.axe.strip"
}
```

Other relevant fields on an entry: `loot` (a loot table id — extra drops on success),
`consume_on_use` (bool), `drop_strategy`, `transform_type` (`single_block` /
`copper_chest`), `particle`, `disallowed_faces`.

### What each tool does (verified extraction)

| Tool  | Rules | Breakdown |
|-------|------:|-----------|
| Axe   | 130 (identical across all tiers) | 25× **strip** (`item.axe.strip`: `oak_log→stripped_oak_log`, `oak_wood→stripped_oak_wood`, …), 45× **scrape** (`item.axe.scrape`: de-oxidise copper one step), 60× **wax_off** (`item.axe.wax_off`: `waxed_x → x`) |
| Hoe   | 3   | `coarse_dirt→dirt`, `rooted_dirt→dirt` (**+ `loot: minecraft:till/rooted_dirt`** → hanging_roots), and `→farmland` (no clean single input — uses a non-`matching_blocks` predicate) |
| Shovel| 1   | `→dirt_path` (no clean single input) |

`consume_on_use` reads as default (true) on every entry, but all 21 carriers are
**non-stackable tools**, so nothing is consumed — the spec note says `consume_on_use`
"only applies to stackable items." The tool is not a material; only the input block is.
So every source here has **one input block → one output block**, no consumed item.

### Value, in tiers

1. **Log/wood stripping (25 pairs) — the gold.** `stripped_*_log`/`stripped_*_wood` are
   extremely common build blocks and today resolve *wrong*: their only source in our
   graph is self-break loot (drops itself → circular). A `stripped_oak_log ← oak_log`
   `IN_WORLD_TRANSFORM` source makes the plan chain correctly to logging.
2. **Hoe tilling with loot (`rooted_dirt→dirt` + hanging_roots) — minor but free** once
   the parser exists.
3. **Copper scrape / wax_off (105 of 130 axe rules) — noise, recommend excluding.**
   Marginal planning value (weathered/waxed copper is time-gated in reality; scrape is a
   half-circular path), high node count, and it introduces reverse-weathering edges the
   graph doesn't otherwise model. Defer or gate behind a flag.

### Two convenient facts

- **The reception machinery already exists.** `block_transformer` *is*
  `IN_WORLD_TRANSFORM`. `SourceType.isConstructive()` already includes it, and the
  self-block-loot penalty was just broadened from "has a recipe sibling" to "has a
  constructive sibling" (synthetic-sources PR #334). So a stripped log's circular
  self-break is penalised **automatically** the moment the transform source exists —
  **no scorer change required.** `GatheringPlan` already maps `IN_WORLD_TRANSFORM →
  ActivityGroup.CRAFT`.
- **No cycle risk right now.** Because *waxing* (`wax_on`) is not yet on the component,
  `wax_off` has no data-driven inverse — so no `copper ↔ waxed_copper` 2-cycle. This can
  change if honeycomb gains the component in a later snapshot; revisit then (the graph's
  structural cycle rejection should cover it, but add a test).

## 4. What this means for the ingestion algorithm, post-26.3

Today `GetServerFilesPipeline` downloads a server JAR and extracts JSON straight out of
it (`data/minecraft/**`). `block_transformer` is **not** in that JSON, so consuming it
requires a genuinely new step:

1. **Run the data generator** on the downloaded JAR:
   `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports`, then read
   `generated/reports/minecraft/components/item/*.json`. This is new capability, not a
   new parser — the ingest machine must now *execute* the JAR, not just unzip it.
   - **JDK version lock:** the generator refuses to run on an older JDK than the JAR was
     built with (26.3-snapshot-2 = class file 69.0 = JDK 25). The ingest environment's
     JDK must be ≥ the target version's. As Minecraft advances this is a moving
     requirement — worth pinning the ingest image to the newest JDK, or selecting a JDK
     per target version.
   - Runtime cost ~1 min per JAR to generate reports; acceptable for the daily
     ledger-driven ingest (only new/changed versions run).
   - **Version gating is free:** pre-26.3 JARs simply emit no `block_transformer` in
     their reports, so the extractor yields nothing for them. No special-casing.
2. **Parse the component reports into `ResourceSource`s** (a new `mc-data` extractor,
   mirroring `SyntheticSources` / the recipe parsers):
   - For each entry, read `if_true.blocks` (input) and `then…state.Name` (output).
     Emit a source: `producedItems = [output ×1]`, `requiredItems = [input ×1]`,
     `type = IN_WORLD_TRANSFORM`. If `loot` is present, resolve that loot table and add
     its averaged drops to `producedItems` (reuse `LootYield`).
   - **Dedupe across tool tiers** — the same (input, output) pair appears on every axe
     material. Emit one source per pair, keyed on the pair, not per tool. Filename
     convention e.g. `block_transform/<output>.json` (parallels `synthetic/<name>.json`).
   - **Skip entries with no `matching_blocks` input** (farmland, dirt_path) — no clean
     single input, and low planning value. Optionally hardcode those few as synthetic if
     ever wanted.
   - **Scope decision:** ship **strip (+ hoe-loot)** first; exclude copper scrape/wax_off
     unless we later decide the weathered-copper chains are worth the noise.
3. **Bump `ExtractionVersion.CURRENT`** (+ a `History:` line) so existing versions
   re-ingest once and pick up the new sources.

Everything downstream is already in place: graph builder treats these like any
`ResourceSource`; `IN_WORLD_TRANSFORM` already scores, groups, and triggers the
self-block penalty.

### Why wait for the full release

This is a snapshot. The component is brand-new and clearly mid-migration (only tools
carry it; waxing isn't on it yet; the farmland/dirt_path rules use predicates we don't
parse). It will very likely gain carriers and rules across the remaining snapshots
(honeycomb waxing, possibly more block interactions) before the autumn release. Building
the extractor now would target a moving format. Capture the approach (this doc), and
implement against the stable release — at which point the ingest-runs-the-generator step
is the one real piece of new architecture, and the strip payload is low-hanging fruit
that drops straight into the existing `IN_WORLD_TRANSFORM` machinery.

## Open decisions for implementation time

1. **Scope:** strip + hoe-loot only, or also copper scrape/wax_off? (Recommend the
   former.)
2. **JDK selection** for the ingest generator step — pin newest, or per-version.
3. **Cycle test** if a later snapshot puts waxing on the component.
