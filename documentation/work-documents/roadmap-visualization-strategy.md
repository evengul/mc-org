# Roadmap Visualization Implementation Strategy

## 📋 Overview

This document outlines the implementation strategy for the roadmap visualization feature in `RoadmapView.kt`. The goal is to create an intuitive, mobile-first dependency graph visualization that helps users understand project relationships and plan parallel work streams.

## 🎯 Design Goals

### Primary Objectives
1. **Clear Dependency Visualization**: Show which projects depend on others
2. **Progress at a Glance**: Indicate project status and blocking relationships
3. **Mobile-First**: Works well on phones (primary use case while playing Minecraft)
4. **Interactive**: Click projects to navigate, see details
5. **Actionable**: Help users identify what can be worked on next

### User Value
- **World Owners**: See critical path, plan work distribution
- **Team Members**: Understand which projects they can start
- **Project Managers**: Track dependencies and blockers

## 🎨 Proposed Visual Approach

### Option A: Vertical Layer List (RECOMMENDED for Mobile)
```
┌─────────────────────────────────┐
│ ROADMAP STATISTICS              │
│ • 5 total projects              │
│ • 2 completed, 1 blocked        │
│ • 2 can start now ✓             │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ LAYER 0 - START HERE (2)        │
│ ┌─────────────────────────────┐ │
│ │ ● Foundation [COMPLETED]    │ │
│ │   Building • 0/10 tasks     │ │
│ │   → Unlocks: Walls          │ │
│ └─────────────────────────────┘ │
│ ┌─────────────────────────────┐ │
│ │ ● Resources [BUILDING]      │ │
│ │   Mining • 5/10 tasks       │ │
│ │   → Unlocks: Decoration     │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ LAYER 1 - NEXT (2)              │
│ ┌─────────────────────────────┐ │
│ │ ● Walls [READY] ✓           │ │
│ │   Building • 0/15 tasks     │ │
│ │   ← Needs: Foundation       │ │
│ │   → Unlocks: Roof           │ │
│ └─────────────────────────────┘ │
│ ┌─────────────────────────────┐ │
│ │ ● Decoration [BLOCKED] ⚠    │ │
│ │   Building • 0/8 tasks      │ │
│ │   ← Needs: Resources        │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│ LAYER 2 - FINAL (1)             │
│ ┌─────────────────────────────┐ │
│ │ ● Roof [BLOCKED] ⚠          │ │
│ │   Building • 0/12 tasks     │ │
│ │   ← Needs: Walls            │ │
│ └─────────────────────────────┘ │
└─────────────────────────────────┘
```

**Pros:**
- Perfect for mobile (vertical scrolling)
- Clear layer grouping
- Easy to understand dependencies
- No complex graph rendering
- Works without JavaScript
- Accessible (screen readers)

**Cons:**
- Doesn't show complex branching visually
- Can't see entire graph at once
- Less "cool factor" than graph visualization

### Option B: SVG Horizontal Flow (Desktop-Optimized)
```
Layer 0         Layer 1         Layer 2
┌──────────┐   ┌──────────┐   ┌──────────┐
│Foundation│──▶│  Walls   │──▶│   Roof   │
│ Complete │   │  Ready   │   │ Blocked  │
└──────────┘   └──────────┘   └──────────┘
                                    
┌──────────┐   ┌──────────┐         
│Resources │──▶│Decoration│         
│ Building │   │ Blocked  │         
└──────────┘   └──────────┘         
```

**Pros:**
- Visual graph representation
- See entire roadmap at once
- Traditional dependency diagram
- Impressive visualization

**Cons:**
- Difficult on mobile (horizontal scroll)
- Requires SVG rendering
- Complex positioning algorithm
- Accessibility challenges
- Harder to maintain

### Option C: Hybrid Approach (RECOMMENDED)
Start with **Option A** (layer list) and add:
- Collapsible layer sections
- Quick filters (show only: blocked, ready, in-progress)
- Summary statistics at top
- Optional: Add simple SVG connectors for desktop view

## 🏗️ Implementation Plan

### Phase 1: Core Layer List View (MVP)
**Files to Create/Modify:**
- `RoadmapView.kt` - Main view implementation
- `roadmap.css` - Component styles
- Helper components for roadmap cards

**Structure:**
```kotlin
fun DIV.roadmapView(tabData: WorldPageTabData.RoadmapData) {
    val (projects, world, worldMember, roadmap) = tabData
    
    // 1. Statistics header
    div("roadmap-stats") {
        roadmapStatistics(roadmap.getStatistics())
    }
    
    // 2. Empty state (if no projects)
    if (roadmap.isEmpty()) {
        emptyState()
        return
    }
    
    // 3. Layers (grouped by depth)
    div("roadmap-layers") {
        roadmap.layers.sortedBy { it.depth }.forEach { layer ->
            roadmapLayer(layer, roadmap)
        }
    }
}
```

### Phase 2: Enhanced Interactivity
- Filter controls (show ready, show blocked, show all)
- Collapsible layers
- Sort within layers (by progress, by name, by type)

### Phase 3: Visual Enhancements (Optional)
- Simple dependency indicators with CSS
- Mini SVG arrows for desktop view
- Animation on layer expand/collapse

## 📦 Component Breakdown

### 1. Roadmap Statistics Card
```kotlin
fun DIV.roadmapStatistics(stats: RoadmapStatistics) {
    div("roadmap-stats-card") {
        div("roadmap-stats-grid") {
            statItem("Total Projects", stats.totalProjects)
            statItem("Completed", stats.completedProjects, "success")
            statItem("In Progress", stats.getInProgressCount(), "action")
            statItem("Blocked", stats.blockedProjects, "warning")
            statItem("Can Start Now", /* calculate ready count */, "success")
        }
        
        if (stats.hasBlockedProjects()) {
            alert("warning") {
                +"${stats.blockedProjects} project(s) waiting on dependencies"
            }
        }
    }
}
```

### 2. Layer Container
```kotlin
fun DIV.roadmapLayer(layer: RoadmapLayer, roadmap: Roadmap) {
    div("roadmap-layer") {
        // Layer header
        div("roadmap-layer-header") {
            h3 {
                when {
                    layer.isRootLayer() -> +"Layer ${layer.depth} - Start Here"
                    layer.depth == roadmap.getMaxDepth() -> +"Layer ${layer.depth} - Final"
                    else -> +"Layer ${layer.depth}"
                }
            }
            span("roadmap-layer-count") {
                +"${layer.projectCount} project(s)"
            }
        }
        
        // Projects in this layer
        div("roadmap-layer-projects") {
            layer.projectIds.forEach { projectId ->
                val node = roadmap.nodes.find { it.projectId == projectId }
                node?.let { roadmapProjectCard(it, roadmap) }
            }
        }
    }
}
```

### 3. Project Card in Roadmap
```kotlin
fun DIV.roadmapProjectCard(node: RoadmapNode, roadmap: Roadmap) {
    div("roadmap-project-card ${getCardModifier(node)}") {
        // Status indicator
        div("roadmap-project-status") {
            statusBadge(node)
        }
        
        // Project header
        div("roadmap-project-header") {
            h4 { +node.projectName }
            infoChip(
                icon = getIconForType(node.projectType),
                text = node.projectType.toPrettyEnumName()
            )
        }
        
        // Progress
        if (node.tasksTotal > 0) {
            progressComponent {
                value = node.tasksCompleted.toDouble()
                max = node.tasksTotal.toDouble()
                label = "${node.tasksCompleted}/${node.tasksTotal} tasks"
            }
        }
        
        // Dependencies (if any)
        if (node.blockingProjectIds.isNotEmpty()) {
            div("roadmap-dependencies") {
                div("roadmap-dependencies-header") {
                    icon(Icons.WARNING)
                    +"Waiting on:"
                }
                ul {
                    node.blockingProjectIds.forEach { depId ->
                        val depNode = roadmap.nodes.find { it.projectId == depId }
                        depNode?.let {
                            li { +it.projectName }
                        }
                    }
                }
            }
        }
        
        // Unlocks (dependent projects)
        if (node.dependentProjectIds.isNotEmpty()) {
            div("roadmap-unlocks") {
                div("roadmap-unlocks-header") {
                    +"Unlocks:"
                }
                ul("roadmap-unlocks-list") {
                    node.dependentProjectIds.forEach { depId ->
                        val depNode = roadmap.nodes.find { it.projectId == depId }
                        depNode?.let {
                            li { +it.projectName }
                        }
                    }
                }
            }
        }
        
        // Action button
        actionButton("View Details") {
            href = Link.Worlds.world(roadmap.worldId).project(node.projectId).to
        }
    }
}

fun getCardModifier(node: RoadmapNode): String {
    return when {
        node.isCompleted() -> "roadmap-project-card--completed"
        node.isBlocked -> "roadmap-project-card--blocked"
        node.isReadyToStart() -> "roadmap-project-card--ready"
        node.isInProgress() -> "roadmap-project-card--in-progress"
        else -> ""
    }
}

fun DIV.statusBadge(node: RoadmapNode) {
    when {
        node.isCompleted() -> chip("success") { +"✓ Completed" }
        node.isBlocked -> chip("warning") { +"⚠ Blocked" }
        node.isReadyToStart() -> chip("success") { +"✓ Ready to Start" }
        node.isInProgress() -> chip("action") { +"⚡ In Progress" }
        else -> chip("neutral") { +"○ Not Started" }
    }
}
```

### 4. Empty State
```kotlin
fun DIV.roadmapEmptyState() {
    emptyState(
        icon = Icons.Menu.ROADMAP,
        title = "No Projects Yet",
        message = "Create your first project to see the roadmap",
        actionText = "Create Project",
        actionHref = Link.Worlds.world(worldId).newProject().to
    )
}
```

## 🎨 CSS Structure

### File: `roadmap.css`
```css
/* Roadmap Container */
.roadmap-stats-card {
    background: var(--clr-surface-default);
    border: 1px solid var(--clr-border-default);
    border-radius: var(--border-radius-md);
    padding: var(--spacing-md);
    margin-bottom: var(--spacing-lg);
}

.roadmap-stats-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: var(--spacing-md);
}

/* Layer Container */
.roadmap-layers {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-lg);
}

.roadmap-layer {
    background: var(--clr-surface-subtle);
    border-radius: var(--border-radius-md);
    padding: var(--spacing-md);
}

.roadmap-layer-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--spacing-md);
    padding-bottom: var(--spacing-sm);
    border-bottom: 2px solid var(--clr-border-default);
}

.roadmap-layer-projects {
    display: grid;
    gap: var(--spacing-md);
    grid-template-columns: 1fr;
}

/* Responsive: 2 columns on tablet, 3 on desktop */
@media (min-width: 768px) {
    .roadmap-layer-projects {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (min-width: 1200px) {
    .roadmap-layer-projects {
        grid-template-columns: repeat(3, 1fr);
    }
}

/* Project Card */
.roadmap-project-card {
    background: var(--clr-surface-default);
    border: 2px solid var(--clr-border-default);
    border-radius: var(--border-radius-md);
    padding: var(--spacing-md);
    display: flex;
    flex-direction: column;
    gap: var(--spacing-sm);
    transition: all 0.2s ease;
}

.roadmap-project-card:hover {
    box-shadow: var(--shadow-lg);
    transform: translateY(-2px);
}

/* Card state modifiers */
.roadmap-project-card--completed {
    border-color: var(--clr-success);
    background: var(--clr-success-subtle);
}

.roadmap-project-card--blocked {
    border-color: var(--clr-warning);
    background: var(--clr-warning-subtle);
}

.roadmap-project-card--ready {
    border-color: var(--clr-success);
    border-width: 3px;
}

.roadmap-project-card--in-progress {
    border-color: var(--clr-action);
}

/* Dependencies section */
.roadmap-dependencies {
    padding: var(--spacing-sm);
    background: var(--clr-warning-subtle);
    border-left: 3px solid var(--clr-warning);
    border-radius: var(--border-radius-sm);
}

.roadmap-unlocks {
    padding: var(--spacing-sm);
    background: var(--clr-info-subtle);
    border-left: 3px solid var(--clr-info);
    border-radius: var(--border-radius-sm);
}

.roadmap-unlocks-list {
    list-style: none;
    padding-left: 0;
    margin: var(--spacing-xs) 0 0 0;
}

.roadmap-unlocks-list li::before {
    content: "→ ";
    color: var(--clr-info);
}
```

## 🔄 Data Flow

```
WorldPage (Route Handler)
    ↓
GetWorldRoadMapStep.process(worldId)
    ↓
Roadmap (domain model)
    ↓
WorldPageTabData.RoadmapData
    ↓
roadmapView(tabData)
    ↓
HTML Output
```

## ✅ Acceptance Criteria

### Must Have (MVP)
- [ ] Display roadmap statistics (total, completed, blocked, in-progress)
- [ ] Group projects by dependency layer
- [ ] Show project status (completed, blocked, ready, in-progress)
- [ ] Display task progress for each project
- [ ] Show which projects are blocking others
- [ ] Show which projects will be unlocked
- [ ] Link to individual project pages
- [ ] Handle empty state (no projects)
- [ ] Mobile-responsive design
- [ ] Uses CSS component architecture (no inline styles)

### Should Have (Phase 2)
- [ ] Filter by status (show ready, show blocked, show all)
- [ ] Collapsible layer sections
- [ ] Sort projects within layers
- [ ] Highlight critical path projects
- [ ] Show estimated completion percentage for world

### Nice to Have (Future)
- [ ] SVG dependency graph view (desktop only)
- [ ] Zoom and pan controls
- [ ] Export roadmap as image
- [ ] Timeline view (Gantt-style)
- [ ] Drag-and-drop to reorder priorities

## 🧪 Testing Strategy

### Manual Testing
1. Empty world (no projects)
2. Single project (no dependencies)
3. Linear dependency chain (A → B → C)
4. Multiple dependencies (A + B → C)
5. Diamond pattern (A → B,C → D)
6. Large world (20+ projects)
7. All projects completed
8. All projects blocked

### Edge Cases
- Project with no tasks
- Project with 100% task completion but not marked complete
- Circular dependencies (shouldn't exist, but verify)
- Very long project names
- Very deep dependency chains (10+ layers)

## 📝 Implementation Checklist

### Step 1: Setup
- [ ] Create `roadmap.css` in `styles/components/`
- [ ] Import in `styles.css`
- [ ] Create helper functions file if needed

### Step 2: Core Components
- [ ] Implement `roadmapStatistics()`
- [ ] Implement `roadmapLayer()`
- [ ] Implement `roadmapProjectCard()`
- [ ] Implement `roadmapEmptyState()`

### Step 3: Main View
- [ ] Implement `roadmapView()` main function
- [ ] Wire up all components
- [ ] Add empty state handling

### Step 4: Styling
- [ ] Add all CSS classes
- [ ] Test responsive breakpoints
- [ ] Verify color contrast
- [ ] Test dark mode (if supported)

### Step 5: Testing
- [ ] Manual testing with test data
- [ ] Mobile device testing
- [ ] Accessibility testing
- [ ] Cross-browser testing

### Step 6: Documentation
- [ ] Add code comments
- [ ] Update user documentation
- [ ] Create usage examples

## 🎯 Recommended Approach

**Start with Option A (Vertical Layer List)** because:
1. ✅ Mobile-first (primary use case)
2. ✅ Simpler to implement and maintain
3. ✅ Works without JavaScript
4. ✅ Accessible by default
5. ✅ Can add visual enhancements later
6. ✅ Easier to test and debug

**MVP Timeline Estimate:**
- Setup + CSS: 1-2 hours
- Core components: 2-3 hours
- Integration + testing: 1-2 hours
- **Total: 4-7 hours**

**Phase 2 Enhancements:** Can be added incrementally based on user feedback.

## 📚 References

- Domain Model: `roadmap-domain-model.md`
- CSS Architecture: `CSS_ARCHITECTURE.md`
- Business Requirements: `BUSINESS_REQUIREMENTS.md`
- Existing Components: `ProjectsView.kt`, `KanbanView.kt`

