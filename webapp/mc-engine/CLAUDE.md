# mc-engine

Game logic — item source graph and the quantity-aware gathering planner.

## Purpose

Models Minecraft's entire item production system as a bipartite graph (`ItemNode` <-> `SourceNode`) and plans
resource gathering over it: which source to use for every item (select) and how much of everything is needed
(quantify). Used by mc-web to power resource planning features.

## Tech

- Depends on: `mc-domain` (compile), `mc-pipeline` (test only)
- Uses `kotlinx-serialization` for graph serialization
- Maven build, JVM 25 target
- Package: `app.mcorg.engine.*`

## Structure

```
model/
  ItemSourceGraph.kt       — Immutable bipartite graph (items <-> sources) with Builder
                             and a lazy producer reverse-index (O(1) getSourcesForItem)
service/
  ItemSourceGraphBuilder.kt — Constructs graph from ResourceSource lists (JSON or DB rows)
plan/
  PlanInputs.kt            — PlanTarget, SupplySource, PlanOverrides, PlanContext
  SelectedDag.kt           — Output of select(): one source decision per item, acyclic
  GatheringPlan.kt         — Quantified plan DAG + derived views (activityList, perTarget),
                             PlanNodeStatus, ActivityGroup ordering rule
  PlanSelector.kt          — select(): cost-driven source choice, supplied terminals,
                             override pins, open tags, structural cycle rejection
  UnitCostModel.kt         — Minutes-to-acquire per item + EffortTable
                             (restricted area — human checkpoint for changes)
  SourceRanking.kt         — Read-only view of UnitCostModel.ranked() for the web layer, so
                             the drill's picker and the planner cannot rank differently
  SelfBlockLoot.kt         — "is breaking this block just re-collecting what you placed?"
  PlanQuantifier.kt        — quantify(): accumulate-then-ceil demand propagation, leftover
                             bank, SurplusPolicy hook; GatheringPlanner facade
```

## Key Concepts

- **ItemNode**: Wraps a `MinecraftId` (item or tag)
- **SourceNode**: Wraps a `ResourceSource.SourceType` + filename (recipe, loot drop, etc.)
- **Edges**: `ItemToSource` (item is required input) and `SourceToItem` (source produces item)
- **Graph is immutable** once built — thread-safe for concurrent queries
- **Quantities tracked** on edges (e.g., "2 planks required", "4 sticks produced")
- **Two-stage planning**: `PlanSelector.select(graph, targets, supplied, overrides, context)` →
  `SelectedDag`; `PlanQuantifier.quantify(dag, targets)` → `GatheringPlan`. Both pure —
  the engine knows nothing about projects, progress, or the database. mc-web nets out
  `collected`, folds farms ∪ linked projects into the labeled `supplied` map, and persists
  only the user's overrides (plans are re-derived on read).
- **PlanNodeStatus**: RESOLVED / RAW_GATHER / SUPPLIED / OPEN_TAG / BLOCKED on every node;
  `GatheringPlan.complete` ⇔ no OPEN_TAG and no BLOCKED.
- **Tag ids carry a `#` prefix** in real data (`#minecraft:planks`); item ids don't.

## Build

```bash
cd webapp && mvn compile -pl mc-engine
mvn test -pl mc-engine
```

## Cost diagnostics — read this before touching `UnitCostModel` or `EffortTable`

The root CLAUDE.md's restricted-area rule says to verify cost changes against real ingested data
rather than reasoning. This is the tool. It is **read-only** and changes no ranking:

```bash
cd webapp && set -a && . ./local.env && set +a
mvn -q -pl mc-web exec:java@cost-diagnostics \
  -Dexec.args="world=3 why iron_ingot iron_nugget cobblestone"
```

`why` (the default) prints what each price is *made of*, in minutes, down the chain — the action,
the availability multiplier, the per-attempt yield and every ingredient. Read the total, find one
you disagree with, then read the lines under it to see which number produced it.

Other modes, all against the same model:

| mode | question it answers |
|------|---------------------|
| `sweep[=<group>]` | move each effort value across its range: what does this number decide? Ends in a plateau verdict per group |
| `picks=<type>` | which items does this source type win, and by how much over the runner-up? |
| `set=<type>:<minutes>` | try a table by hand without rebuilding the engine |
| `grain` | per-source vs per-type effort — where the grain is too coarse |
| `activities` | how many distinct kinds of work the plan adds up to |
| `projects=<ids>` | carry a real project's item set through every row |

**An `INERT` or "no plateau" verdict is a claim about the *version*, not the constant.** No 1.x
version ingests villager trades, so the trade rows read inert on 1.21.4 for months while moving
27 selections on 26.2.0 (MCO-524). Check the version carries the source type before believing a
row decides nothing:

```bash
psql ... -c "SELECT version, count(*) FROM resource_source GROUP BY 1"
```

*(This section described `score-diagnostics` and the `SelectionScorer` factor breakdown until
MCO-490 deleted that model. The additive scorer is gone; there is no second model to diff
against, which is why every mode above measures one model on its own terms.)*

**`exec:java` resolves siblings from `~/.m2`, not the reactor**, so install first or you will
measure stale jars and not notice:

```bash
mvn -q install -DskipTests -pl mc-domain,mc-pipeline,mc-engine
```

`-am` does **not** help here — the MCO-285 note's `-am` fix applies to `-pl` test/compile runs,
not to `exec:java`.

## Tests

Located in `src/test/kotlin/app/mcorg/engine/`. Graph building/model tests plus the planner suites:
`PlanSelectorTest` (selection mechanism), `PlanQuantifierTest` (quantity propagation),
`ActivityOrderingTest` (roadmap ordering), and `CuratedSelectionTest` (pinned expectations for
real Minecraft acquisition chains — see `documentation/work-documents/fable-mc-engine-scoring-audit.md`).

### A curated expectation is only as good as the source set it models

`CuratedSelectionTest` fixtures are hand-built lists of `ResourceSource`. If a fixture omits a
source that exists in real data, the test can **pass for the wrong reason** — and keep passing
while production is broken.

This is not hypothetical. MCO-317: `iron_ingot - smelting raw iron beats unpacking blocks,
packing nuggets, and chest loot` passed for a long time because `ironChain` gave `iron_nugget`
only *circular* sources (unpack an ingot). The selector rejected `iron_ingot_from_nuggets`
structurally, so smelting won by default and the scorer was never actually exercised. Real data
has a non-circular nugget source — smelting iron equipment via a tag — and with it the planner
routed an entire build's iron through 296,515 nuggets. Adding that one source to the fixture
turned the test red immediately.

So when adding or trusting a curated expectation:

- Check the fixture against the **real** source set for that item (the diagnostic above lists
  every candidate, which is the fastest way to see what you left out).
- Ask whether the expectation holds because of the scorer, or because the alternative was
  structurally impossible in your fixture. Only the first is a real test.
- After a scoring change, sweep the constant across its plausible range and confirm which tests
  fail at which values. A value that passes may still be sitting on a tie-break with zero margin
  — that is how MCO-317's first attempt at 15 got caught.
