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

## Tests

Located in `src/test/kotlin/app/mcorg/engine/`. Graph building/model tests plus the planner suites:
`PlanSelectorTest` (selection mechanism), `PlanQuantifierTest` (quantity propagation),
`ActivityOrderingTest` (roadmap ordering), and `CuratedSelectionTest` (pinned expectations for
real Minecraft acquisition chains — see `documentation/work-documents/fable-mc-engine-scoring-audit.md`).
