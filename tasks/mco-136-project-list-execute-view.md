---
linear-issue: MCO-136
status: approved
phase: 1
created: 2026-03-11
---

# Feature 7a — Project List Page (Execute View + Empty State + Create Flow)

## Summary

Rewrite the project list page at `/worlds/:worldId/projects` using the new DSL and design tokens.
Execute view only — project cards with task progress, status badge, resource progress bar, and next
incomplete task. Includes two-card empty state and create project modal. Plan view and session toggle
are Feature 7b (MCO-TBD).

## User Value

**Personas:** Casual player, Worker, Technical player — all land on this page after selecting a world.

Workers and casual players see execute-oriented cards (status, progress, next task) by default —
they immediately know what to work on next without navigating into each project.

New users see two equal-weight entry points (create / browse ideas) instead of a single dominant
CTA, reducing decision friction.

## Scope

**Included:**
- Full-page rewrite of `GET /worlds/:worldId/projects` using `pageShell`, `appHeader`, DSL components
- `GET /worlds/:worldId` → 301 redirect to `/worlds/:worldId/projects` (replaces existing `handleGetWorld()`)
- Project cards (execute view): project name (linked to detail), status badge, task progress text,
  resource progress bar (`.progress--lg`, 6px), next incomplete task name
- Resource aggregates + next task fetched in a single extended SQL query — no N+1
- Two-card empty state (`.empty-state-cards`): "Plan your own project" → create modal,
  "Browse community ideas" → `/ideas`
- Create project modal: name (required, 3–100 chars), description (optional, 0–500 chars),
  type (required, `ProjectType` enum) — reuses existing `CreateProjectPipeline`
- On create success: new card prepended via HTMX; empty state removed via `hx-swap-oob` if showing
- Done projects sorted to bottom, visually dimmed
- Breadcrumb: `Worlds › [World Name]`
- Gear icon → `/worlds/:worldId/settings` (visible to all members; settings page enforces ADMIN+ access)
- Mobile: cards stack full-width, 44px min tap targets on buttons

**Excluded (Feature 7b):**
- Plan view cards
- Session-level PLAN/EXEC toggle
- Fragment endpoint `/worlds/:worldId/projects/list-fragment`

**Also excluded:**
- BLOCKED status badge (Phase 2 — requires dependency resolution)
- Project search/filter (separate follow-up)
- Kanban/roadmap tabs (separate features)

## Behaviour

### Project cards (execute view)

Each `.project-card` shows:
- **Project name** — IBM Plex Mono, weight 600, linked to `/worlds/:worldId/projects/:projectId`
- **Status badge** — derived from `ProjectStage` via a template-layer mapping function
  `ProjectStage.toDisplayStatus()`: IDEA/DESIGN/PLANNING → NOT_STARTED,
  RESOURCE_GATHERING/BUILDING/TESTING → IN_PROGRESS, COMPLETED → DONE. No BLOCKED in Phase 1.
- **Task progress** — `{done} / {total} tasks` in `--text-muted`, `--text-xs`
- **Resource progress bar** — `.progress--lg` (6px), full green when `gathered >= required`
- **Next incomplete task** — single line truncated, `--text-muted`, `--text-xs`. Empty if no tasks.

Done projects: `.project-card--done` renders name in `--text-disabled`, reduced opacity, sorted last.

**Sort order:** IN_PROGRESS first, then NOT_STARTED, then DONE. Within each group, alphabetical.

### Empty state

When the world has zero projects, show two equal-weight cards (`.empty-state-cards`):
- **"Plan your own project"** — opens create project modal
- **"Browse community ideas"** — navigates to `/ideas`

Mobile: stacked vertically, equal size.

### Create project flow

- Trigger: "Create Project" button in toolbar (or empty state CTA)
- `<dialog>` modal via `modalForm()` DSL
- Fields: Name (required), Description (optional), Type (required, `ProjectType` select)
- `hx-post` to `/worlds/:worldId/projects`
- On success: new card prepended to list; if empty state was showing, removed via `hx-swap-oob`
- On validation error: inline field errors (existing pattern)
- Demo users: create button disabled with notice

### Breadcrumb / header

Desktop: `[MC-ORG] Worlds › [World Name] ... Ideas ⚙`
Mobile: `[World Name] ⚙`

### Mobile behaviour

- Cards stack full-width
- Empty state cards stack vertically, equal size
- Create button full-width on mobile
- Min tap targets: 44px for CTA buttons

## Technical Approach

### Route & handler

Replace the existing `handleGetWorld()` call at `GET /worlds/{worldId}` with a 301 redirect:
```kotlin
get {
    val worldId = call.parameters["worldId"]!!.toInt()
    call.respondRedirect("/worlds/$worldId/projects", permanent = true)
}
```

Add new handler inside the existing `route("/projects")` block:
```kotlin
get {
    call.handleGetProjectList()
}
```

The old `GetWorldPipeline.kt` and its templates become dead code after this change —
leave with a `// TODO Feature 13: remove with cleanup` comment.

### Pipeline

```
GetWorldStep         → fetch world by worldId
GetWorldMemberStep   → fetch membership + role (for toolbar button visibility)
GetProjectListStep   → new step: projects with aggregates (single query)
```

### GetProjectListStep — SQL

Single query using LEFT JOINs and a correlated subquery. Correct table and column names:

```sql
SELECT
  p.id,
  p.name,
  p.stage,
  COUNT(DISTINCT t.id) FILTER (WHERE t.completed = false) AS tasks_remaining,
  COUNT(DISTINCT t.id)                                      AS tasks_total,
  COALESCE(SUM(rg.required), 0)                            AS resources_required,
  COALESCE(SUM(rg.collected), 0)                           AS resources_gathered,
  (
    SELECT t2.name FROM action_task t2
    WHERE t2.project_id = p.id AND t2.completed = false
    ORDER BY t2.position ASC   -- verify column exists; fall back to t2.id ASC if not
    LIMIT 1
  ) AS next_task_name
FROM projects p
LEFT JOIN action_task t  ON t.project_id  = p.id
LEFT JOIN resource_gathering rg ON rg.project_id = p.id
WHERE p.world_id = :worldId
GROUP BY p.id
ORDER BY
  CASE p.stage
    WHEN 'RESOURCE_GATHERING' THEN 1
    WHEN 'BUILDING'           THEN 2
    WHEN 'TESTING'            THEN 3
    WHEN 'IDEA'               THEN 4
    WHEN 'DESIGN'             THEN 5
    WHEN 'PLANNING'           THEN 6
    WHEN 'COMPLETED'          THEN 7
  END,
  p.name ASC
```

> **Note:** Verify `action_task.position` column exists before using. If absent, use `t2.id ASC`.

### Domain model

New `ProjectListItem` in `mc-domain` under `app.mcorg.domain.model.project`:

```kotlin
data class ProjectListItem(
    val id: Int,
    val name: String,
    val stage: ProjectStage,
    val tasksTotal: Int,
    val tasksDone: Int,
    val resourcesRequired: Int,
    val resourcesGathered: Int,
    val nextTaskName: String?
)
```

### Template (DSL)

New file: `mc-web/.../presentation/templated/dsl/pages/ProjectListPage.kt`

Implement `projectCard()` in the **existing stub** at
`mc-web/.../presentation/templated/dsl/ProjectCard.kt` (do not create a new file).

Stage-to-status mapping as a template-layer utility (not a domain change):
```kotlin
fun ProjectStage.toDisplayStatus(): String = when (this) {
    ProjectStage.IDEA, ProjectStage.DESIGN, ProjectStage.PLANNING -> "not-started"
    ProjectStage.RESOURCE_GATHERING, ProjectStage.BUILDING, ProjectStage.TESTING -> "in-progress"
    ProjectStage.COMPLETED -> "done"
}
```

### CSS

New stylesheets:
- `/static/styles/components/project-card.css` — `.project-card`, `.project-card__header`,
  `.project-card__name`, `.project-card__meta`, `.project-card--done`
  Card hover: `border-color: #3a4060; background: var(--bg-raised)`
- `/static/styles/pages/project-list.css` — toolbar layout, card list spacing

### Create project HTMX response

Check `HX-Request` header in the response from `handleCreateProject`:
- If HTMX request: return new project card fragment (prepend target) + `hx-swap-oob` to remove empty state
- Otherwise: redirect to `/worlds/:worldId/projects` (fallback for non-JS)

### No migration required

All data comes from existing tables (`projects`, `action_task`, `resource_gathering`) via a new query.

### Modules touched

- `mc-domain`: new `ProjectListItem`
- `mc-web`: new handler, new step, new page template, new CSS, route changes

## Sub-tasks

- [ ] 1. Add `ProjectListItem` to `mc-domain`
- [ ] 2. Create `GetProjectListStep` with extended SQL (verify `action_task.position` first)
- [ ] 3. Implement `projectCard()` in existing `ProjectCard.kt` stub (execute view)
- [ ] 4. Implement `projectListPage()` full-page template + `ProjectStage.toDisplayStatus()`
- [ ] 5. Create `project-card.css` and `project-list.css`
- [ ] 6. Wire routes: replace `GET /worlds/{worldId}` with redirect; add `GET /worlds/{worldId}/projects`
- [ ] 7. Update `handleCreateProject` to return card fragment on HTMX request
- [ ] 8. Integration tests: page load (projects exist + empty), create project flow

## Acceptance Criteria

- [ ] `GET /worlds/:worldId/projects` renders the new project list page
- [ ] `GET /worlds/:worldId` returns 301 to `/worlds/:worldId/projects`
- [ ] Each card shows: linked name, status badge, task progress text, resource progress bar, next task
- [ ] Status badge derived from `ProjectStage` via template mapping (no domain enum change)
- [ ] Empty state shows two equal-weight cards when world has zero projects
- [ ] Create project modal works; on success new card prepended, empty state removed
- [ ] Done projects sorted last, visually dimmed
- [ ] Breadcrumb: `Worlds › [World Name]`
- [ ] Gear icon visible to all world members, links to settings
- [ ] Mobile layout correct (cards stack, 44px tap targets)
- [ ] All existing tests pass; new integration tests cover page load and create flow
- [ ] `mvn clean compile` passes with zero errors

## Out of Scope

| Item | Reason |
|------|--------|
| Plan view | Feature 7b |
| Session-level toggle | Feature 7b |
| BLOCKED badge | Phase 2 — requires dependency resolution |
| Project search/filter | Separate follow-up |
| Per-project view preference | Feature 9 (project detail) |

## Tech Lead Review

**Verdict:** Changes recommended (all incorporated above)

**Key corrections applied:**
1. SQL table names corrected: `projects` (not `project`), `resource_gathering` (not `resource_item`),
   columns `required`/`collected` (not `required_amount`/`gathered_amount`)
2. Verify `action_task.position` before using in ORDER BY
3. Route: replace existing `handleGetWorld()` with redirect (old pipeline left as dead code with TODO)
4. Gear icon: no role check in `appHeader` — settings page enforces access itself
5. Stage→badge mapping is a template-layer function, not a domain enum
6. `projectCard()` implemented in existing stub, not a new file
7. `handleCreateProject` uses `HX-Request` header to decide fragment vs redirect response
