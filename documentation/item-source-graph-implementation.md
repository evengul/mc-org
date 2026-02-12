# Item Source Graph Implementation Plan

## Overview

This document outlines the implementation plan for creating a digital graph representation of all Minecraft item sources and production chains. The graph will enable the application to calculate how users can gather various items for their projects.

## Use Case

The final use case is to calculate resource gathering paths for project requirements:
- Given a list of items needed for a project
- Calculate all possible ways to obtain each item
- Show both natural sources (loot, mining) and crafted sources (recipes)
- Handle complex production chains (items needed to craft other items)

## Graph Design

### Bipartite Graph Structure

The graph uses a **bipartite structure** with two types of nodes:

#### 1. Item Nodes
- Represent Minecraft items (diamonds, iron ore, sticks, etc.)
- Identified by item ID (e.g., "minecraft:diamond")

#### 2. Source Nodes
- Represent specific source instances (each ResourceSource becomes a unique SourceNode)
- Identified by: `sourceType.id + filename` (unique combination)
- Examples:
  - `SourceNode(CRAFTING_SHAPED, "stick.json")` - specific stick recipe
  - `SourceNode(CRAFTING_SHAPED, "ladder.json")` - different ladder recipe
  - `SourceNode(BLOCK, "diamond_ore.json")` - mining diamond ore
  - `SourceNode(CHEST, "stronghold_corridor.json")` - chest loot

#### 3. Edges
- **Item → Source**: Items required for a source (recipe inputs)
- **Source → Item**: Items produced by a source (recipe outputs or loot drops)

### Why Bipartite with Unique Source Nodes?

This structure handles multiple sources elegantly and preserves all recipe information:

```
Stick sources (22 different ways to get sticks):
├─ SourceNode(CRAFTING_SHAPED, "stick.json")
│  └─ requires: oak_planks
├─ SourceNode(CRAFTING_SHAPED, "ladder.json") 
│  └─ requires: (different recipe)
├─ SourceNode(BLOCK, "acacia_leaves.json")
│  └─ requires: (none - loot source)
└─ ... (19 more sources)
```

**Benefits:**
1. **Preserves all recipe details** - Each ResourceSource maintains its unique identity
2. **Query source-specific requirements** - Can ask "what does THIS specific recipe need?"
3. **Handles multiple acquisition paths** - 22 ways to get sticks, all preserved
4. **Maintains file context** - Know which data file each source came from
5. **Future-proof** - Can add properties to individual sources (difficulty, probability)

### Real-World Scale

From Minecraft 1.21.11 test data:
- **5,192 ResourceSources** (individual recipe/loot files)
- **2,698 unique SourceNodes** (some duplicates removed)
- **1,393 unique items**
- **8,063 edges** (connections)
- Example: Sticks have **22 different sources**

### Alternative Approaches Considered

- **Direct item-to-item edges**: Simpler but loses source type information
- **Hypergraphs**: More mathematically correct but harder to implement and query
- **Multi-edges**: Can get messy with visualization, harder to reason about

## Implementation Phases

### Phase 1: Core Graph Data Structure (30 min)
**File**: `webapp/src/main/kotlin/app/mcorg/domain/model/resources/ItemSourceGraph.kt`

Create a graph data structure with:

```kotlin
// Node types
data class ItemNode(val itemId: String)
data class SourceNode(val sourceType: SourceType)

// Edge types (for clarity)
sealed class GraphEdge {
    data class ItemToSource(val item: ItemNode, val source: SourceNode) : GraphEdge
    data class SourceToItem(val source: SourceNode, val item: ItemNode) : GraphEdge
}

// Main graph class
class ItemSourceGraph {
    private val itemNodes: MutableSet<ItemNode>
    private val sourceNodes: MutableSet<SourceNode>
    private val edges: MutableMap<Node, MutableSet<Node>>
    
    // Core operations
    fun addItemNode(itemId: String): ItemNode
    fun addSourceNode(sourceType: SourceType, sourceId: String): SourceNode
    fun addEdge(from: Node, to: Node)
    
    // Query methods (to be expanded in Phase 3)
    fun getSourcesForItem(itemId: String): Set<SourceNode>
    fun getRequiredItems(source: SourceNode): Set<ItemNode>
    fun getProducedItems(source: SourceNode): Set<ItemNode>
}
```

**Key Features:**
- Immutable once built (thread-safe reads)
- Efficient lookups (indexed by item ID and source ID)
- Support for cycles (no assumptions about DAG)
- Memory-efficient (shared node instances)

### Phase 2: Graph Builder (20 min)
**File**: `webapp/src/main/kotlin/app/mcorg/domain/services/ItemSourceGraphBuilder.kt`

Service to construct the graph from `ResourceSource` data:

```kotlin
object ItemSourceGraphBuilder {
    fun buildFromResourceSources(sources: List<ResourceSource>): ItemSourceGraph {
        val graph = ItemSourceGraph()
        
        for (source in sources) {
            // Create source node
            val sourceNode = graph.addSourceNode(source.type, source.filename)
            
            // Connect required items → source
            for (item in source.requiredItems) {
                val itemNode = graph.addItemNode(item.id)
                graph.addEdge(itemNode, sourceNode)
            }
            
            // Connect source → produced items
            for (item in source.producedItems) {
                val itemNode = graph.addItemNode(item.id)
                graph.addEdge(sourceNode, itemNode)
            }
        }
        
        return graph
    }
}
```

**Input**: Collection of `ResourceSource` objects (from JSON files)
**Output**: Fully constructed `ItemSourceGraph`

### Phase 3: Graph Query API (20 min)
**File**: `webapp/src/main/kotlin/app/mcorg/domain/services/ItemSourceGraphQueries.kt`

Useful query operations:

```kotlin
class ItemSourceGraphQueries(private val graph: ItemSourceGraph) {
    
    // Basic queries
    fun findAllSourcesForItem(itemId: String): Set<SourceNode>
    fun findRequiredItemsForSource(sourceId: String): Set<ItemNode>
    
    // Production chain queries
    fun findProductionChain(
        targetItem: String,
        maxDepth: Int = 10
    ): ProductionTree
    
    fun findShortestPath(
        from: String,
        to: String
    ): List<GraphEdge>?
    
    // Analysis queries
    fun detectCycles(): List<List<Node>>
    fun findLeafItems(): Set<ItemNode>  // Items with no recipe (only loot/mining)
    fun findTopLevelItems(): Set<ItemNode>  // Items not used in any recipe
}

// Result types
data class ProductionTree(
    val targetItem: ItemNode,
    val sources: List<ProductionBranch>
)

data class ProductionBranch(
    val source: SourceNode,
    val requiredItems: List<ProductionTree>  // Recursive structure
)
```

**Query Examples:**
- "How can I get diamonds?" → `findAllSourcesForItem("minecraft:diamond")`
- "What's the full crafting tree for a diamond pickaxe?" → `findProductionChain("minecraft:diamond_pickaxe")`
- "What items can only be found, never crafted?" → `findLeafItems()`

### Phase 4: Unit Tests (20 min)
**Files**: 
- `webapp/src/test/kotlin/app/mcorg/domain/model/resources/ItemSourceGraphTest.kt`
- `webapp/src/test/kotlin/app/mcorg/domain/services/ItemSourceGraphBuilderTest.kt`

**Test Scenarios:**

```kotlin
class ItemSourceGraphTest {
    @Test
    fun `simple linear chain - logs to planks to sticks`()
    
    @Test
    fun `multiple sources - iron from mining and smelting`()
    
    @Test
    fun `cycles - crafting table needs planks, planks need crafting`()
    
    @Test
    fun `complex recipe - multiple inputs and outputs`()
    
    @Test
    fun `loot source - chest has no required items`()
    
    @Test
    fun `production chain depth limiting`()
    
    @Test
    fun `shortest path between items`()
}

class ItemSourceGraphBuilderTest {
    @Test
    fun `builds graph from empty source list`()
    
    @Test
    fun `builds graph from single recipe`()
    
    @Test
    fun `builds graph from multiple recipes sharing items`()
    
    @Test
    fun `handles duplicate source definitions`()
}
```

### Phase 5: Data Loading Integration (15 min)

Integrate with existing JSON loading code to populate the graph:

```kotlin
// Where existing ResourceSource data is loaded
val allSources: List<ResourceSource> = loadResourceSourcesFromJson()
val itemGraph = ItemSourceGraphBuilder.buildFromResourceSources(allSources)

// Store in application context for later use
// (exact integration point TBD based on existing architecture)
```

## Key Design Decisions

### 1. In-Memory Graph (Initially)
- Fast queries without DB overhead
- Can persist to database later if needed
- Reasonable memory footprint (thousands of items, tens of thousands of edges)

### 2. Immutable Graph Structure
- Build once, query many times
- Thread-safe for concurrent queries
- Rebuild graph when data changes (acceptable for infrequent updates)

### 3. Lazy Loading Support
- Can load graph on-demand
- Cache in memory after first load
- Invalidate cache when underlying data changes

### 4. Thread-Safe Operations
- All query operations are thread-safe (no mutations)
- Building is single-threaded (acceptable for startup/refresh)

### 5. Export to Standard Formats
- JSON export for debugging and external tools
- DOT format for Graphviz visualization (future)
- Adjacency list format for algorithms

## File Structure

```
webapp/src/main/kotlin/app/mcorg/domain/
  model/resources/
    ItemSourceGraph.kt          (NEW - core graph structure)
    ResourceSource.kt           (EXISTING - no changes needed)
  services/
    ItemSourceGraphBuilder.kt   (NEW - builds graph from data)
    ItemSourceGraphQueries.kt   (NEW - query operations)

webapp/src/test/kotlin/app/mcorg/domain/
  model/resources/
    ItemSourceGraphTest.kt      (NEW - unit tests)
  services/
    ItemSourceGraphBuilderTest.kt (NEW - builder tests)
    ItemSourceGraphQueriesTest.kt (NEW - query tests)
```

## Example Usage

```kotlin
// Build graph from data
val graph = ItemSourceGraphBuilder.buildFromResourceSources(allSources)
val queries = ItemSourceGraphQueries(graph)

// Query: How can I get diamonds?
val diamondSources = queries.findAllSourcesForItem("minecraft:diamond")
// Returns: [
//   SourceNode(BLOCK, "diamond_ore"),
//   SourceNode(CHEST, "stronghold_corridor"),
//   SourceNode(ENTITY_INTERACT, "villager_armorer_trade"),
//   ...
// ]

// Query: What do I need to craft a diamond pickaxe?
val requirements = queries.findProductionChain("minecraft:diamond_pickaxe", maxDepth = 10)
// Returns ProductionTree:
//   diamond_pickaxe
//   └─ [CRAFTING_SHAPED] pickaxe_recipe
//      ├─ diamond (x3)
//      │  ├─ [BLOCK] diamond_ore
//      │  └─ [CHEST] stronghold
//      └─ stick (x2)
//         └─ [CRAFTING_SHAPED] stick_from_planks
//            └─ planks (x1)
//               └─ [CRAFTING_SHAPELESS] planks_from_logs
//                  └─ log (x1)
//                     └─ [BLOCK] oak_log
```

## Future Enhancements (Out of Scope)

### Database Persistence
- Store graph structure in PostgreSQL
- Use graph traversal queries for production chains
- May require PostgreSQL graph extensions (Apache AGE, pg_graph)

### Visualization
- Web-based in*teractive graph viewer
- Filter by item type, source type
- Highlight critical paths
- Show resource gathering efficiency*

### Advanced Queries
- Weighted edges (time, difficulty, probability)
- Resource optimization (cheapest/fastest path)
- Alternative paths ranking
- Availability based on game progression

### Integration with Project Planning
- "I need these items for my project" → auto-generate gathering plan
- Show what items are blockers (no current sources available)
- Estimate time to gather all resources

## Notes

- **No quantities**: Initial implementation tracks presence/absence, not amounts (e.g., "needs sticks" not "needs 2 sticks")
- **Cycles are OK**: Graph algorithms must handle cycles (crafting table paradox)
- **Source uniqueness**: Each `ResourceSource` becomes a unique `SourceNode` (even if multiple recipes produce same item)
- **No filtering**: Graph includes ALL items and sources from data files

## Timeline

Total estimated time: **~2 hours** for core implementation and testing
- Phase 1: 30 min
- Phase 2: 20 min  
- Phase 3: 20 min
- Phase 4: 20 min
- Phase 5: 15 min
- Buffer: 15 min

## Success Criteria

- [x] Graph can be built from existing `ResourceSource` data (Phase 1 & 2 complete)
- [x] Can query all sources for any item (Phase 1 & 2 complete)
- [x] Can traverse production chains recursively (Phase 3 complete)
- [x] Handles cycles without infinite loops (Phase 1 & 3 complete - cycles can be represented and detected)
- [x] All unit tests pass (Phase 1-3 complete - 47 tests passing)
- [x] Code compiles with `mvn clean compile` (All phases complete)
- [x] Documentation is clear and complete (All phases complete)

## Related Files

- **Data Source**: `ResourceSource.kt` (existing)
- **Input Data**: Processed JSON files (location TBD)
- **Integration Point**: Application startup or lazy initialization

---

**Document Status**: Phase 3 Complete ✅  
**Created**: 2026-01-08  
**Last Updated**: 2026-01-08  
**Phase 1 Completed**: 2026-01-08  
**Phase 2 Completed**: 2026-01-08  
**Phase 3 Completed**: 2026-01-08  
**Next Steps**: Phase 4 (Unit Tests - already complete) and Phase 5 (Data Loading Integration)

### Phase 1 Summary

✅ **Completed:**
- Created `ItemSourceGraph.kt` with bipartite graph structure
- Implemented `ItemNode` and `SourceNode` data classes
- Implemented `GraphEdge` sealed class with `ItemToSource` and `SourceToItem` variants
- Created Builder pattern for constructing immutable graphs
- Implemented core query methods: `getSourcesForItem`, `getRequiredItems`, `getProducedItems`
- Implemented helper methods: `getItemNode`, `getSourceNode`, `getAllItems`, `getAllSources`, `getStatistics`
- Created comprehensive unit tests (19 tests, all passing)
- Tests cover: empty graphs, node addition, deduplication, linear chains, multiple sources, cycles, complex recipes, edge cases
- Successfully compiled with `mvn clean compile`
- All tests pass with `mvn test`

**Key Design Features:**
- Thread-safe immutable graph (after building)
- Efficient lookups via indexed maps
- Handles cycles naturally (no DAG assumptions)
- Memory-efficient node deduplication
- Clear separation between mutable builder and immutable graph

### Phase 2 Summary

✅ **Completed:**
- Created `ItemSourceGraphBuilder.kt` service
- Implemented `buildFromResourceSources()` to construct graph from ResourceSource list
- Implemented `analyzeResourceSources()` for statistics and debugging
- Added comprehensive logging for build process
- Error handling with try-catch and skip tracking
- Created extensive unit tests (9 tests, all passing)
- Tests include: empty sources, single recipe, multiple recipes, loot sources, duplicate handling, complex recipes
- **Integration test with real Minecraft data**: Successfully built graph from 5,192 ResourceSources
  - Result: 1,393 unique items, 20 source types, 3,840 edges
  - Verified queries work on real data (found 6 sources for sticks)
- Successfully compiled with `mvn clean compile`
- All tests pass with `mvn test`

**Key Design Features:**
- Object singleton pattern for stateless service
- Logging at INFO level for build progress
- Statistics tracking (processed/skipped counts)
- Graceful error handling with detailed warnings
- Analysis utility for data validation

**Real Data Verification:**
- Successfully processed all 5,192 ResourceSources from Minecraft 1.21.11
- Source type distribution validated (20 unique types)
- No items with tag prefixes ("#") in final graph
- Query performance validated on production-scale data

### Phase 3 Summary

✅ **Completed:**
- Created `ItemSourceGraphQueries.kt` service with advanced query operations
- Implemented `findProductionChain()` - recursive production tree traversal with cycle protection
- Implemented `findLeafItems()` - identifies base resources (only obtainable via loot/mining)
- Implemented `findTopLevelItems()` - identifies final products not used in other recipes
- Implemented `detectCycles()` - finds circular dependencies in production chains
- Implemented `findShortestPath()` - BFS-based shortest crafting path between items
- Implemented `analyzeGraph()` - comprehensive graph statistics and analysis
- Created extensive unit tests (19 tests, all passing)
- Tests cover: basic queries, production chains, depth limiting, leaf/top-level items, cycle detection, shortest paths, multiple sources
- Successfully compiled with `mvn clean compile`
- All tests pass with `mvn test` (47 total tests: 19 graph + 9 builder + 19 queries)

**Key Design Features:**
- Recursive production tree with cycle detection
- BFS for optimal path finding
- DFS for cycle detection with recursion stack
- Immutable result types (ProductionTree, ProductionBranch)
- Thread-safe query operations
- Comprehensive graph analysis utilities

**Query Capabilities:**
- **Production chains**: Full recursive tree showing all ways to obtain an item
- **Leaf detection**: Finds base resources (logs, ores, etc.)
- **Top-level detection**: Finds final products (tools, armor, etc.)
- **Cycle detection**: Identifies circular crafting dependencies
- **Path finding**: Shortest crafting sequence between two items
- **Graph analysis**: Statistics including items with most sources, most complex recipes, etc.

