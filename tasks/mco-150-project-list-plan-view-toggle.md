---
linear-issue: MCO-150
type: feature
status: approved
created: 2026-03-12
epic: tasks/epics/frontend-rewrite.md
depends-on: MCO-136 Feature 7a
---

# Project List Page — Plan View + Toggle

## Summary

Add a plan view variant of the project list page and a Plan/Execute toggle that swaps between
the two views via HTMX fragment endpoint. The active view is reflected in the URL as a query
parameter (`?view=plan|execute`) — no cookie or DB persistence required. Plan view project
cards show resource definition count, dependency summary (blocked-by / blocks counts), and
project location instead of the execute view's task progress and resource progress bar.

## User Value

**Persona:** Technical player managing multiple projects with inter-project dependencies.

The plan view gives a planning-oriented overview across all projects at a glance — which
projects have resources defined, which are blocked, which block others, and where they are
located in the world — without opening each project individually.

Without this, the project list is execute-only. Technical players must open each project to
assess planning status. The IA principle of "plan and execute are views, not modes" is
unfulfilled at the project list level.

## Scope

**Included:**
- Plan view project card component (resource count, dependency summary, location)
- Plan/Execute toggle in the projects toolbar, wired to HTMX
- `?view=plan|execute` query parameter reflecting active view; defaults to `execute` if absent
- `hx-push-url` keeps the browser URL in sync on toggle
- `GET /worlds/:worldId/projects/list-fragment?view=plan|execute` fragment endpoint
- New `ProjectPlanListItem` domain model
- New pipeline step querying resource counts, dependency counts, and location per project
- Plan view card returned from create project when form includes `view=plan`

**Excluded:**
- Cookie or DB-persisted view preference for the project list
- Resource definition inline editing (Phase 2)
- Production path status on plan cards (Phase 2 — path generation not built)
- Editing project location (deferred — display only in Phase 1)
- "Mark plan complete" flag (Phase 2+ — completeness is user-defined, no DB field)

## Behaviour

### DOM structure change (sub-task 0)

`#projects-content` is restructured to separate the HTMX swap target from the create modal:

```
#projects-content
  #projects-view          ← toggle targets this with hx-swap="outerHTML"
    #projects-toolbar-slot
      [toolbar with toggle, only when projects exist]
    [empty state, only when no projects]
    #project-card-list    ← always rendered (even empty) for HTMX afterbegin insert
  [create-project-modal]  ← outside swap target, never destroyed by toggle
```

### Toggle placement and interaction

The Plan/Execute toggle appears in the projects toolbar (inside `#projects-toolbar-slot`),
only when projects exist. It uses `.toggle` / `.toggle__btn` / `.toggle__btn--active` CSS
classes and is wired with HTMX:

- Clicking PLAN sends `GET /worlds/:worldId/projects/list-fragment?view=plan`
- Clicking EXEC sends `GET /worlds/:worldId/projects/list-fragment?view=execute`
- `hx-target="#projects-view"`, `hx-swap="outerHTML"`
- `hx-push-url="/worlds/:worldId/projects?view=plan|execute"` (explicit URL, not `true`,
  because the request URL is `.../list-fragment` not the page URL)
- The fragment response is the full `<div id="projects-view">...</div>` replacement
- The toggle re-renders with the correct active state inside the fragment

### View resolution

On any request to `/worlds/:worldId/projects` or the fragment endpoint:
- Read `view` query param. If `plan`, render plan view. Otherwise render execute view.
- Default: `execute` if param is absent or invalid.
- No cookie, no session state.

Navigating away and back resets to execute view (no persistence). Execute is the primary
view and the correct default.

### Plan view project cards

Each card shows:

```
[Project Name]                         [badge: NOT_STARTED / IN_PROGRESS / DONE]
12 resources defined                   Blocked by: 2 projects
Overworld (120, 64, -340)              Blocks: 1 project
```

- **Project name:** links to `/worlds/:worldId/projects/:projectId`
- **Status badge:** same stage → badge mapping as execute view
- **Resource definition count:** count of `resource_gathering` rows for this project.
  Display: `N resources defined` or `No resources defined` if zero.
- **Location:** always present (NOT NULL in DB, defaults to `OVERWORLD (0, 0, 0)`).
  Formatted as `Dimension (x, y, z)` — e.g. `Overworld (120, 64, -340)`.
- **Dependency summary:** blocked-by count (rows in `project_dependencies` where
  `project_id = p.id`) and blocks count (rows where `depends_on_project_id = p.id`).
  Each line omitted when count is zero.

### Plan view sort order

1. Projects with zero resource definitions first (completely unplanned)
2. Then by blocked-by count descending (most constrained projects first)
3. Then alphabetical by project name

Note: The IA spec references "incomplete plans first" but plan completeness is user-defined
with no DB representation in Phase 1. This sort is a reasonable approximation.

### Empty state

When no projects exist, the empty state from Feature 7a renders regardless of `?view=` param.
The toggle does not appear when there are no projects.

### Fragment endpoint

`GET /worlds/:worldId/projects/list-fragment?view=plan|execute` returns the full
`<div id="projects-view">...</div>` element for outerHTML swap. Contains toolbar (toggle
in correct active state) and the card list for the requested view.
If `view` param is missing or invalid, defaults to `execute`.

### Create project in plan view

The create project modal form includes a hidden `view` input. When the modal opens, the
onclick sets the hidden input value from the current URL: `new URLSearchParams(window.location.search).get('view') || 'execute'`.

On form submit with `view=plan`, `handleCreateProject` returns a plan card fragment
(via `GetProjectPlanListItemStep`) instead of the execute card. The OOB toolbar update
(`projectsToolbarOobFragment`) also receives the view so the toggle renders in PLAN active.

## Technical Approach

### New domain model (`mc-domain`)

```kotlin
data class ProjectPlanListItem(
    val id: Int,
    val name: String,
    val stage: ProjectStage,
    val resourceDefinitionCount: Int,
    val blockedByCount: Int,
    val blocksCount: Int,
    val location: MinecraftLocation   // NOT NULL in DB
)
```

### New pipeline steps (`mc-web`)

**`GetProjectPlanListStep(worldId)`** — list step. Single aggregate query:

```sql
SELECT p.id, p.name, p.stage,
       p.location_dimension, p.location_x, p.location_y, p.location_z,
       COUNT(DISTINCT rg.id)         AS resource_definition_count,
       COUNT(DISTINCT pd_blocked.id) AS blocked_by_count,
       COUNT(DISTINCT pd_blocks.id)  AS blocks_count
FROM projects p
LEFT JOIN resource_gathering rg
       ON rg.project_id = p.id
LEFT JOIN project_dependencies pd_blocked
       ON pd_blocked.project_id = p.id
LEFT JOIN project_dependencies pd_blocks
       ON pd_blocks.depends_on_project_id = p.id
WHERE p.world_id = ?
GROUP BY p.id
ORDER BY
  CASE WHEN COUNT(DISTINCT rg.id) = 0 THEN 0 ELSE 1 END ASC,
  COUNT(DISTINCT pd_blocked.id) DESC,
  p.name ASC
```

**`GetProjectPlanListItemStep`** — single-project plan item (used by create project handler).
Same query but `WHERE p.id = ?`.

### New DSL component (`mc-web`)

`planProjectCard()` and `planProjectCardList()` extension functions on `FlowContent` in a
new `PlanProjectCard.kt` under `presentation/templated/dsl/`. Both use `id = "project-card-list"`
on the list wrapper (same ID as execute view — required for create project HTMX insert).

### Toggle update (`Toggle.kt`)

`planExecuteToggle(worldId: Int, active: String)` — updated signature. Emits `hx-get`,
`hx-target="#projects-view"`, `hx-swap="outerHTML"`, and `hx-push-url` with explicit page URL.

### Page restructure (`ProjectListPage.kt`)

- `projectsContent()` now renders `#projects-view` (containing toolbar + cards) and the
  modal separately
- `projectsViewContent()` is the swappable inner content (toolbar slot + empty state/cards)
- `projectsViewFragment()` returns the full `<div id="projects-view">` for the fragment endpoint
- `projectsToolbarOobFragment(worldId, view)` updated with new params for toggle
- `projectsToolbar(worldId, view)` updated to include the toggle

### Updated handler (`GetProjectListPipeline.kt`)

Reads `?view=` param and branches between `GetProjectListStep` and `GetProjectPlanListStep`.

### New fragment handler (`GetProjectListFragmentPipeline.kt`)

`handleGetProjectListFragment()` — reads `?view=`, fetches appropriate list, returns
`projectsViewFragment(...)`. Registered at `GET /worlds/:worldId/projects/list-fragment`.

### Updated create handler (`CreateProjectPipeline.kt`)

Reads `view` from form params. When `view=plan`, fetches via `GetProjectPlanListItemStep`
and returns `planProjectCardFragment`. Updates `projectsToolbarOobFragment` call to pass
`worldId` and `view`.

### New route (`WorldHandler.kt`)

```kotlin
get("/list-fragment") {
    call.handleGetProjectListFragment()
}
```
Added inside `route("/projects")`, before `route("/{projectId}")`.

### Skills to load

`/docs-development`, `/add-endpoint`, `/docs-htmx`, `/docs-product`, `/docs-testing`

## Sub-tasks

- [ ] 0. Restructure `ProjectListPage.kt`: add `#projects-view` inner wrapper, move modal
         outside it; update `projectsToolbar` to accept `worldId` + `view`, wire toggle;
         add `projectsViewFragment()`; update `projectsToolbarOobFragment(worldId, view)`
- [ ] 1. Add `ProjectPlanListItem` data class to `mc-domain`
- [ ] 2. Create `GetProjectPlanListStep` (list) and `GetProjectPlanListItemStep` (single)
         in `pipeline/project/commonsteps/`
- [ ] 3. Create `planProjectCard()` and `planProjectCardList()` DSL components in
         `PlanProjectCard.kt`
- [ ] 4. Update `planExecuteToggle()` in `Toggle.kt` — add `worldId` param and HTMX attrs
- [ ] 5. Update `handleGetProjectList()` — read `?view=`, branch on execute/plan fetch
- [ ] 6. Create `handleGetProjectListFragment()` in `GetProjectListFragmentPipeline.kt`
- [ ] 7. Add `view` hidden input to create project modal; update onclick buttons to set it
         from URL; update `handleCreateProject` to read `view`, return correct card type,
         pass view to `projectsToolbarOobFragment`
- [ ] 8. Register `GET /worlds/:worldId/projects/list-fragment` route in `WorldHandler.kt`
- [ ] 9. Integration test: fragment endpoint returns correct view for `?view=plan` and
         `?view=execute`; missing param defaults to execute
- [ ] 10. Integration test: full page load with `?view=plan` renders plan view;
          absent param renders execute view

## Acceptance Criteria

- [ ] Plan/Execute toggle appears in toolbar when projects exist; absent on empty state
- [ ] Clicking PLAN swaps to plan view via HTMX without full page reload
- [ ] Clicking EXEC swaps to execute view via HTMX without full page reload
- [ ] Browser URL updates to `?view=plan` / `?view=execute` on toggle (`hx-push-url`)
- [ ] Create project modal stays in DOM after toggle (it is outside `#projects-view`)
- [ ] Plan cards show: name (linked), status badge, resource definition count, location,
      dependency summary
- [ ] Dependency count lines omitted when count is zero
- [ ] Location always displayed as `Dimension (x, y, z)` — no dash fallback
- [ ] Full page load with `?view=plan` renders plan view
- [ ] Full page load with absent or invalid `?view` renders execute view
- [ ] Empty state renders identically regardless of `?view`; toggle not shown
- [ ] `list-fragment?view=plan` returns plan view fragment; `?view=execute` returns execute
- [ ] Fragment endpoint requires authentication and world membership
- [ ] Creating a project in plan view inserts a plan card (not execute card)
- [ ] Toggle uses `.toggle` / `.toggle__btn` / `.toggle__btn--active` CSS classes
- [ ] No cookies set by this feature

## Out of Scope

| Item                            | Reason                                                              |
|---------------------------------|---------------------------------------------------------------------|
| Cookie/DB-persisted list toggle | URL param is stateless and GDPR-safe; no persistence needed         |
| Edit project location           | Display-only in Phase 1; editing deferred                           |
| Production path status on cards | Phase 2 — path generation not built yet                             |
| Resource definition inline edit | Phase 2 — "Inline Resource Planning" epic                           |
| "Mark plan complete" flag       | Phase 2+ — plan completeness is user-defined, no DB representation  |

## Tech Lead Review

**Verdict:** Approved (changes recommended — all incorporated above)

**Key corrections applied:**
1. `#projects-view` inner wrapper added as sub-task 0 to prevent modal destruction on swap
2. Create project returns plan card when `view=plan` (via hidden form input + `GetProjectPlanListItemStep`)
3. Location NOT NULL confirmed — no dash fallback needed
4. `hx-push-url` uses explicit URL (not `true`) since request URL is `.../list-fragment`
5. `hx-swap="outerHTML"` on `#projects-view` (cleaner than innerHTML + string concat)
