# mc-engine

Game logic — item source graph, production chain queries, and path suggestion scoring.

## Purpose

Models Minecraft's entire item production system as a bipartite graph (`ItemNode` <-> `SourceNode`) and provides query/optimization services over it. Used by mc-web to power resource planning features.

## Tech

- Depends on: `mc-domain` (compile), `mc-pipeline` (test only)
- Uses `kotlinx-serialization` for graph serialization
- Maven build, JVM 21 target
- Package: `app.mcorg.engine.*`

## Structure

```
model/
  ItemSourceGraph.kt       — Immutable bipartite graph (items <-> sources) with Builder
  ResourceGatheringPlan.kt — Output model for planned resource gathering
service/
  ItemSourceGraphBuilder.kt  — Constructs graph from ServerData (recipes, loot tables)
  ItemSourceGraphQueries.kt  — Graph traversal: find sources, trace production chains
  PathSuggestionScorer.kt    — Scores and ranks item acquisition paths
  PathSuggestionService.kt   — High-level service combining queries + scoring
```

## Key Concepts

- **ItemNode**: Wraps a `MinecraftId` (item or tag)
- **SourceNode**: Wraps a `ResourceSource.SourceType` + filename (recipe, loot drop, etc.)
- **Edges**: `ItemToSource` (item is required input) and `SourceToItem` (source produces item)
- **Graph is immutable** once built — thread-safe for concurrent queries
- **Quantities tracked** on edges (e.g., "2 planks required", "4 sticks produced")

## Build

```bash
cd webapp && mvn compile -pl mc-engine
mvn test -pl mc-engine
```

## Tests

Located in `src/test/kotlin/app/mcorg/engine/`. Tests for graph building, queries, scoring, and path suggestion service.
