# Roadmap Domain Model - Design Documentation

## Overview

The Roadmap domain model provides a comprehensive representation of project dependencies within a world, enabling visualization and analysis of the dependency graph.

## Core Components

### 1. Roadmap
The main container that holds the complete dependency graph for a world.

**Properties:**
- `worldId`: The ID of the world this roadmap belongs to
- `worldName`: The name of the world for display purposes
- `nodes`: List of all projects in the world (as RoadmapNode)
- `edges`: List of all dependency relationships (as RoadmapEdge)
- `layers`: List of horizontal layers for visualization (as RoadmapLayer)

**Key Methods:**
- `getRootNodes()`: Returns projects with no dependencies (starting points)
- `getLeafNodes()`: Returns projects with no dependents (endpoints)
- `getMaxDepth()`: Returns the longest dependency chain depth
- `isEmpty()`: Checks if roadmap has any projects
- `getStatistics()`: Returns comprehensive statistics about the roadmap

### 2. RoadmapNode
Represents a single project in the dependency graph.

**Properties:**
- `projectId`: The unique project ID
- `projectName`: Display name
- `projectType`: Type of project (Building, Contraption, etc.)
- `stage`: Current lifecycle stage
- `tasksTotal`: Total number of tasks in the project
- `tasksCompleted`: Number of completed tasks
- `isBlocked`: Whether this project is blocked by incomplete dependencies
- `blockingProjectIds`: List of dependency project IDs that are blocking this project
- `dependentProjectIds`: List of project IDs that depend on this project
- `layer`: The horizontal layer/depth in the dependency graph (0 = root)

**Key Methods:**
- `getCompletionPercentage()`: Calculates task completion %
- `isReadyToStart()`: Checks if all dependencies are met
- `isInProgress()`: Checks if project is actively being worked on
- `isCompleted()`: Checks if project is finished
- `getBlockingCount()`: Number of blocking dependencies
- `getDependentCount()`: Number of dependent projects

### 3. RoadmapEdge
Represents a dependency relationship between two projects.

**Properties:**
- `fromNodeId`: The dependent project ID (the one waiting)
- `fromNodeName`: Name of the dependent project
- `toNodeId`: The dependency project ID (the one being waited on)
- `toNodeName`: Name of the dependency project
- `isBlocking`: Whether this dependency is currently blocking progress

**Direction Semantics:**
- Arrow direction: `fromNode` → `toNode` means "fromNode depends on toNode"
- Example: If Building depends on Foundation, then:
  - `fromNodeId` = Building
  - `toNodeId` = Foundation
  - Meaning: "Building cannot start until Foundation is completed"

### 4. RoadmapLayer
Represents a horizontal layer in the dependency graph visualization.

**Properties:**
- `depth`: The layer depth (0 = root layer, higher numbers = deeper in the chain)
- `projectIds`: List of project IDs in this layer
- `projectCount`: Number of projects in this layer

**Purpose:**
Projects in the same layer have the same dependency depth and can theoretically be worked on in parallel.

### 5. RoadmapStatistics
Aggregated statistics about the roadmap for quick insights.

**Properties:**
- `totalProjects`: Total number of projects in the world
- `completedProjects`: Number of completed projects
- `blockedProjects`: Number of projects blocked by dependencies
- `rootProjects`: Number of projects with no dependencies
- `leafProjects`: Number of projects with no dependents
- `maxDepth`: Maximum dependency depth
- `totalDependencies`: Total number of dependency relationships

**Key Methods:**
- `getOverallCompletionPercentage()`: Overall world completion %
- `getInProgressCount()`: Projects actively being worked on
- `hasBlockedProjects()`: Whether any projects are blocked

## Design Principles

### 1. Recursive Structure
The model uses a graph-based approach with nodes and edges, allowing for:
- Efficient traversal of dependency chains
- Multiple parents (a project can depend on multiple other projects)
- Multiple children (a project can be depended on by multiple projects)
- Clear visualization paths

### 2. Layer-Based Organization
Projects are organized into layers based on their dependency depth:
- **Layer 0 (Root)**: Projects with no dependencies - can start immediately
- **Layer 1**: Projects that depend only on Layer 0 projects
- **Layer N**: Projects that depend on projects in Layer N-1
- This enables parallel work planning within each layer

### 3. Business Rule Enforcement
The model enforces critical business rules:
- **No Circular Dependencies**: The layer system prevents cycles
- **World-Scoped Dependencies**: All dependencies must be in the same world
- **Blocking Detection**: Automatically identifies which projects are blocked

### 4. Immutability
All data classes are immutable, ensuring:
- Thread safety
- Predictable state
- Easier testing
- Clear data flow

## Usage Scenarios

### Scenario 1: Visualizing Dependency Graph
```kotlin
val roadmap = // ... loaded from repository
val rootNodes = roadmap.getRootNodes() // Projects to start with
val layers = roadmap.layers // Organize by parallel work groups
```

### Scenario 2: Finding Critical Path
```kotlin
val roadmap = // ... loaded from repository
val depth = roadmap.getMaxDepth() // Longest dependency chain
val criticalPath = // Trace through layers to find longest path
```

### Scenario 3: Progress Tracking
```kotlin
val roadmap = // ... loaded from repository
val stats = roadmap.getStatistics()
val completion = stats.getOverallCompletionPercentage()
val blocked = stats.blockedProjects
```

### Scenario 4: Work Planning
```kotlin
val roadmap = // ... loaded from repository
val readyProjects = roadmap.nodes.filter { it.isReadyToStart() }
// These projects can be started immediately
```

## Implementation Notes

### Next Steps
1. **Repository Layer**: Create repository to build Roadmap from database
   - Query all projects in a world
   - Query all dependencies
   - Calculate layers using topological sort
   - Detect blocking status based on dependency stages

2. **Service Layer**: Create business logic for roadmap operations
   - Generate roadmap from world ID
   - Cache roadmap calculations
   - Update roadmap when project stages change

3. **Presentation Layer**: Create HTML visualization
   - SVG-based dependency graph
   - Interactive node clicking
   - Filter by project type/stage
   - Zoom and pan capabilities

4. **API Endpoints**: Create routes for roadmap access
   - `GET /worlds/{worldId}/roadmap` - Returns full roadmap
   - HTMX fragments for interactive updates

### Performance Considerations
- Roadmap calculation involves graph algorithms (topological sort)
- Consider caching for large worlds (50+ projects)
- Incremental updates when single project changes
- Pagination for very large dependency graphs

### Testing Strategy
- ✅ Unit tests for domain model (completed)
- Repository tests with sample dependency graphs
- Integration tests for roadmap generation
- UI tests for visualization rendering

## Business Value

### For World Owners
- Clear visibility of project dependencies
- Identify critical path projects
- Plan parallel work streams
- Track overall world progress

### For Team Members
- Understand which projects can be started
- See how their work enables other projects
- Avoid dependency conflicts
- Coordinate with team members

### For Project Managers
- Resource allocation planning
- Risk identification (blocked projects)
- Progress reporting
- Dependency management

## Related Models

- **Project**: Individual projects that become nodes
- **ProjectDependency**: Raw dependency relationships that become edges
- **World**: Container for all projects
- **ProjectStage**: Lifecycle stages used to determine blocking status
- **ProjectType**: Categorization used in visualization

