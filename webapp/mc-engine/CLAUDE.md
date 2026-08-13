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
  PlanSelector.kt          — select(): scorer-driven source choice, supplied terminals,
                             override pins, open tags, structural cycle rejection
  SelectionScorer.kt       — Candidate scoring (restricted area — human checkpoint for changes)
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

## Score diagnostics — read this before touching `SelectionScorer`

The root CLAUDE.md's restricted-area rule says to verify scoring changes against real ingested
data rather than reasoning. This is the tool. It is **read-only** and changes no ranking:

```bash
cd webapp && set -a && . ./local.env && set +a
mvn -q -pl mc-web exec:java@score-diagnostics \
  -Dexec.args="world=11 demand=64 iron_ingot iron_nugget cobblestone"
```

It prints, per item, every candidate source in the order `PlanSelector` would rank them, with
the factor breakdown that produced each total (`base 95  thr +50  recip -13  req -10  depth -5`)
and the selected one marked `▶`. Args: `world=<id>` or `version=<mc version>` to pick the graph,
`demand=<n>` (default 64 — raise above the recipe threshold of 100 to see the bulk bonus), then
item ids (`sand` expands to `minecraft:sand`).

**`exec:java` resolves siblings from `~/.m2`, not the reactor**, so install first or you will
measure stale jars and not notice:

```bash
mvn -q install -DskipTests -pl mc-domain,mc-pipeline,mc-engine
```

`-am` does **not** help here — that is the other half of the MCO-285 note and it applies to
`-pl` test/compile runs, not to `exec:java`.

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
