---
linear-issue: MCO-138
status: approved
phase: 1
created: 2026-03-13
---

# Feature 9: Project Detail — Execute View

## Summary
Rewrite the project detail page at `/worlds/:worldId/projects/:projectId` to render an execute view using the new DSL components and dark-theme design tokens. The execute view shows resource rows with counter increments (+1/+64/+1728) and decrements (-1/-64/-1728), progress bars, source labels, search/sort controls, and a task checklist below resources. The plan/execute toggle is wired to per-project DB persistence. This is the primary working surface for casual players gathering resources.

## User value
**Personas served:** Casual player (primary), Worker (secondary), Technical player (tertiary — uses toggle to switch to plan view).

**What the user does differently:** The casual player opens a project on their phone while mining and taps counter buttons one-handed to track gathered resources. Today they use the old tab-based project page which buries resources behind a tab click and uses the legacy component system. After this feature, resources are the primary content — visible immediately on load — with large mobile tap targets, progress bars, source labels, and search/sort for navigating large resource lists. The worker gets the same streamlined execute view with no planning complexity visible.

**What happens if we don't build it:** The casual player persona has no complete experience. Project detail is the terminal destination of every navigation path — without it, the project list page leads to a dead end (or falls back to the old UI which violates the new IA).

## Scope

### Included
- New project detail page template using `pageShell()`, `appHeader()`, and new DSL components
- Execute view as default: resource list (primary), task checklist (secondary)
- Resource rows: item name, progress bar (4px), collected/required count, source label, symmetric counter buttons (-1728/-64/-1/+1/+64/+1728)
- Completed resource row treatment (strikethrough, disabled, green progress bar)
- Search bar above resources (filter by item name, client-side via JS using `data-item-name` attributes)
- Sort options above resources: by name (A-Z), by progress % (low to high, high to low), client-side via JS using `data-progress-pct` attributes
- Free-entry field: tapping the count value opens inline input to set absolute collected value
- Task checklist with checkbox toggle (check/uncheck via HTMX)
- Add task inline (secondary button, HTMX POST)
- Plan/execute toggle in project header, wired to DB persistence via a single GET endpoint that persists preference and returns content
- Plan view stub: when preference is `plan`, render a "Plan view coming soon" placeholder
- Mobile project detail header: back arrow, project name, toggle
- Desktop project header: project name, status badge, location, progress bar, toggle
- Breadcrumb: Worlds > [World Name] > [Project Name]
- Status badge using existing `ProjectStage` mapping
- Rename existing `done-more` endpoint to `edit-done`, accepting signed amounts (any non-zero integer)
- New `PUT .../collected` endpoint for absolute value set from free-entry
- New `GET .../detail-content?view=plan|execute` endpoint for toggle content swap with preference persistence
- Add `scripts` parameter to `pageShell()` for client-side JS loading

### Excluded
- Plan view content (Feature 10 — separate spec)
- Partial dependency notice (Phase 2 — automation)
- Production path link (Phase 2)
- Dependencies section (Phase 2)
- Inline resource definition (Phase 2)
- Location editing (exists in old UI, can be deferred to a settings/detail spec)
- Old route redirect from `/app/projects/:projectId` (Feature 13 cleanup)

## Behaviour

### Page load
1. User navigates to `/worlds/:worldId/projects/:projectId`
2. Server reads per-project view preference from `user_project_view_preference` table (default: `execute`)
3. If preference is `execute`: render execute view (this spec)
4. If preference is `plan`: render page with header and toggle, but main content area shows a centered placeholder: "Plan view coming soon" in muted text with an IBM Plex Mono heading, using the `.empty-state` pattern. The toggle remains functional so the user can switch back to execute.

### Execute view layout (desktop)
```
[Logo]   Worlds › [World Name] › [Project Name]     Ideas   ⚙️

[Project Name]                              [PLAN | EXEC]
[Status badge]  Location: X:120, Z:-430    [████████░░ 32/64]

─── RESOURCES TO GATHER ───────────────────────────────────

[🔍 Search items...]   Sort: [Name ▲] [Progress ▲▼]

  Iron Ingot     ████████░░  32 / 64    [-1728][-64][-1][+1][+64][+1728]   Source: Iron Farm
  Redstone Dust  ░░░░░░░░░░   0 / 8     [-1728][-64][-1][+1][+64][+1728]   Source: Manual gather
  Oak Planks     ██████████  16 / 16  ✓                                     Source: Manual gather

─── TASKS ─────────────────────────────────────────────────

  ☐  Lay out foundation
  ☐  Place hoppers
  ☑  Dig out area
  [+ Add task]
```

### Execute view layout (mobile)
```
←  [Project Name]          [PLAN|EXEC]

[Status badge]   ████████░░  32/64

─── RESOURCES TO GATHER ──────────

[🔍 Search items...]
Sort: [Name ▲] [Progress ▲▼]

  Iron Ingot
  ████████░░  32 / 64
  Source: Iron Farm
  [-1728][-64][-1][ 32 ][+1][+64][+1728]

  Redstone Dust
  ░░░░░░░░░░   0 / 8
  Source: Manual gather
  [-1728][-64][-1][ 0 ][+1][+64][+1728]

─── TASKS ────────────────────────

  ☐  Lay out foundation
  ...
```

Minus buttons (-1728/-64/-1) are expected to be used rarely. If mobile space is tight at implementation time, revisit the layout — possible approaches include wrapping to two rows or hiding minus buttons behind a toggle. Do not cut minus buttons without a product decision.

### Resource search and sort behaviour
- **Search bar**: text input above the resource list. Filters resource rows by item name (case-insensitive substring match). Implemented client-side using JavaScript that reads `data-item-name` attributes on each `.resource-row` element and sets `display: none` on non-matching rows. No server round-trip.
- **Sort options**: buttons or a select control. Options:
  - Name A-Z (default)
  - Progress % low to high
  - Progress % high to low
- Sort is client-side: JavaScript reads `data-progress-pct` attributes and reorders DOM nodes within the resource list container.
- Search and sort are independent — search filters the currently sorted list.
- After HTMX swaps (e.g. counter updates that re-render a resource row), the JS must re-apply the active search filter. This is handled by listening to `htmx:afterSwap` on the resource list container.
- On mobile: search bar is full-width, sort options render as a compact row below the search bar.

### Counter increment/decrement behaviour
- All counter operations use a single endpoint: `PATCH /worlds/:worldId/projects/:projectId/resources/gathering/:resourceGatheringId/edit-done`
- The endpoint accepts a signed `amount` parameter (any non-zero integer):
  - `+1`: `amount=1`
  - `+64`: `amount=64`
  - `+1728`: `amount=1728` (1 shulker box = 27 slots × 64 items)
  - `-1`: `amount=-1`
  - `-64`: `amount=-64`
  - `-1728`: `amount=-1728`
- Server-side clamping via SQL: `GREATEST(LEAST(collected + ?, required), 0)` — increment caps at `required`, decrement floors at 0.
- Validation: `amount` must be a non-zero integer. Reject `amount=0` with a 400 response.
- HTMX response: returns the updated resource row progress fragment + OOB swap for the overall project progress bar. The OOB swap target ID must match the element ID used in the new `ProjectDetailPage.kt` template.
- Scale flash animation: count value scales 1 → 1.1 → 1 over 100ms on update. Implemented via a CSS class (e.g. `.counter-flash`) applied to the count element in the HTMX response fragment, with a CSS `@keyframes` animation that auto-completes.

### Free-entry absolute set behaviour
- Tapping the count display (e.g. "32 / 64") replaces it with an inline `<input type="number" min="0" max="{required}">` pre-filled with the current collected value.
- On blur or Enter: submits via HTMX `PUT /worlds/:worldId/projects/:projectId/resources/gathering/:resourceGatheringId/collected` with `value={entered value}`
- Server-side clamping: value clamped to range `[0, required]`
- On Escape: reverts to the count display without submitting
- Response: same fragment swap pattern as `edit-done` (row progress + OOB overall progress bar)

### Completed resource row
- When `collected >= required`: row gets `.resource-row--complete`
- Item name: `--text-disabled` + strikethrough
- Progress bar: full green (`--green`) via `.progress__fill--complete`
- Counter buttons: hidden (not just disabled — reclaim the space on mobile)
- Row remains visible but visually depressed

### Task checklist behaviour
- Each task renders as a `.task-row` with `.task-checkbox`
- Checking/unchecking: HTMX PATCH to existing task toggle endpoint
- Done task: `--text-disabled`, strikethrough (`.task-row--done`)
- "+ Add task" button at bottom: `.btn--secondary`, reveals an inline text input. On Enter or blur (if non-empty): HTMX POST to create task. On Escape or blur (if empty): collapse input.

### Plan/execute toggle behaviour
- Toggle buttons use `hxGet` targeting `#project-content`:
  - PLAN button: `GET /worlds/:worldId/projects/:projectId/detail-content?view=plan`
  - EXEC button: `GET /worlds/:worldId/projects/:projectId/detail-content?view=execute`
- The `detail-content` endpoint both persists the view preference to DB (via `SetViewPreferenceStep`) and returns the appropriate content fragment (execute view or plan stub).
- The response replaces only `#project-content` — the page header, breadcrumb, and toggle itself are not re-rendered. The toggle active state is updated via OOB swap in the response.
- Toggle is fixed-width, pill shape, IBM Plex Mono 12px uppercase.

### Empty states
- No resources: section shows "No resources defined yet." in muted text. No add-resource button in execute view (resource definition belongs to plan view, Phase 2). Search/sort controls are hidden when there are no resources.
- No tasks: section shows empty `.task-list` with only the "+ Add task" button visible.
- Plan view stub: centered `.empty-state` with heading "Plan view" and body "Coming soon — use the execute view to track your progress." Toggle remains functional.

### Edge cases
- Resource with `required = 0`: filter out, do not render
- Counter increment beyond required: clamped by SQL `LEAST(collected + ?, required)`
- Counter decrement below 0: clamped by SQL `GREATEST(collected + ?, 0)`
- Free-entry value outside [0, required]: clamped server-side
- Free-entry non-numeric input: HTML `type="number"` prevents this client-side; server validates and returns 400 if invalid
- Project not found: 404 page
- User not a world member: 403 via existing auth plugin
- Project with no resources AND no tasks: show both empty states stacked
- Search with no matches: show "No resources match your search." in muted text within the resource list area
- HTMX counter swap while search is active: `htmx:afterSwap` handler re-applies the current search filter so updated rows are not incorrectly revealed

## Technical approach

### Modules touched
- **mc-web** (primary): new template, handler modifications, new/modified pipeline steps, CSS, JS, routes
- **mc-domain**: no changes (existing `ResourceGatheringItem`, `ActionTask`, `Project` models suffice)

### New template files
- `presentation/templated/dsl/pages/ProjectDetailPage.kt` — new page using `pageShell()`, `appHeader()`, `container()`
- Flesh out stub DSL components: `ResourceRow.kt`, `TaskList.kt` (currently empty stubs)
- New DSL component: `ResourceSearch.kt` — search input and sort controls

### Layout change
- Add `scripts: List<String> = emptyList()` parameter to `pageShell()` in `Layout.kt`, following the existing `stylesheets` pattern. Script tags rendered at end of `<head>` with `defer` attribute.

### New JS file
- `/static/scripts/resource-search.js` — client-side filter and sort logic:
  - Filter: reads `data-item-name` attributes on `.resource-row` elements, hides non-matching rows via `display: none`
  - Sort: reads `data-progress-pct` attributes (integer 0–100) and reorders DOM nodes using `parentElement.append(...sortedElements)`
  - Handles `htmx:afterSwap` on the resource list container to re-apply active search filter after counter updates
  - Follows the pattern established by `searchable-select.js`

### Handler changes
- Rewrite `handleGetProject()` in `GetProjectPipeline.kt` or create new `handleGetProjectDetail()`:
  - Read view preference via `GetViewPreferenceStep`
  - If execute: load resources + tasks together
  - If plan: render stub
  - Render using new `projectDetailPage()` template
- Rename `done-more` to `edit-done`: update route, modify validation, modify SQL
- New endpoint `PUT .../collected`: new pipeline with validation, SQL to set absolute collected value
- New endpoint `GET .../detail-content?view=plan|execute`: persists preference via `SetViewPreferenceStep`, returns content fragment + OOB toggle state update

### Existing endpoint rename
- `PATCH .../done-more` becomes `PATCH .../edit-done`
- The old `done-more` path no longer exists. The legacy `ResourcesTab.kt` reference to `/done-more` will be cleaned up in Feature 13 alongside other old UI removal.

### Routes (final state)
- `GET /worlds/:worldId/projects/:projectId` — rewritten handler, new template
- `GET /worlds/:worldId/projects/:projectId/detail-content?view=plan|execute` — new, toggle content swap with preference persistence
- `PATCH /worlds/:worldId/projects/:projectId/resources/gathering/:id/edit-done` — renamed from `done-more`, accepts signed amounts
- `PUT /worlds/:worldId/projects/:projectId/resources/gathering/:id/collected` — new, absolute set
- Task toggle and create endpoints — existing, unchanged

### CSS files needed
- `/static/styles/components/resource-row.css` — new
- `/static/styles/components/task-list.css` — new
- `/static/styles/components/resource-search.css` — new
- `/static/styles/pages/project-detail.css` — new

### Database changes
- None. All required tables exist.

### Skills to load
- `/docs-product`, `/docs-development`, `/docs-htmx`, `/add-endpoint`

## Sub-tasks

- [ ] 1. Implement `ResourceRow.kt` DSL component: item name, progress bar, count display (tappable for free-entry), source label, symmetric counter buttons (-1728/-64/-1/+1/+64/+1728), completed state, mobile stacked layout. Each `.resource-row` element must include `data-item-name="{itemName}"` and `data-progress-pct="{collectedPct}"` attributes.
- [ ] 2. Implement `TaskList.kt` DSL component: task rows with checkboxes, done state, "+ Add task" button with inline input reveal.
- [ ] 3. Implement `ResourceSearch.kt` DSL component: search input, sort option buttons/select.
- [ ] 4. Add `scripts: List<String> = emptyList()` parameter to `pageShell()` in `Layout.kt`. Create `/static/scripts/resource-search.js` implementing client-side filter (using `data-item-name`) and sort (using `data-progress-pct`), matching the `searchable-select.js` pattern. Handle `htmx:afterSwap` on the resource list container to re-apply active search filter after counter updates.
- [ ] 5. Create `ProjectDetailPage.kt` template: page shell (with `resource-search.js` in scripts list), header with toggle + status badge + location + overall progress bar, resource section (with search/sort), task section, empty states, plan view stub.
- [ ] 6. Create CSS files: `resource-row.css` (counter button tap targets, completed state, `.counter-flash` keyframes), `task-list.css`, `resource-search.css`, `project-detail.css` (mobile header with back arrow).
- [ ] 7. Rename `done-more` to `edit-done`: update route path, change validation from `validateRange("amount", 1, Int.MAX_VALUE)` to reject only zero, change SQL to `GREATEST(LEAST(collected + ?, required), 0)`. Ensure OOB swap target ID matches the new template's progress bar element.
- [ ] 8. Add `PUT .../collected` endpoint: pipeline step, validation (clamp to [0, required]), SQL `UPDATE resource_gathering SET collected = LEAST(GREATEST(?, 0), required) WHERE id = ?`, HTMX response fragments.
- [ ] 9. Add `GET .../detail-content?view=plan|execute` endpoint: validate `view` param, persist preference via `SetViewPreferenceStep`, return content fragment (execute or plan stub) + OOB toggle active state. Wire toggle buttons in `ProjectDetailPage.kt` with `hxGet` targeting `#project-content`.
- [ ] 10. Rewrite `handleGetProject()`: read view preference, branch on execute vs plan stub, render using new `projectDetailPage()` template.
- [ ] 11. Wire task checklist: HTMX PATCH for toggle, HTMX POST for add-task with inline input pattern.
- [ ] 12. Integration tests: page load (execute), page load (plan stub), increment via `edit-done`, decrement via `edit-done` (floor at 0), absolute set via `collected` (clamp to [0, required]), `detail-content` preference persistence + correct content per view, task toggle, `amount=0` rejection (400).

## Acceptance criteria
- [ ] `/worlds/:worldId/projects/:projectId` renders execute view with dark theme and design tokens
- [ ] Resources shown with item name, progress bar, count, source label
- [ ] +1/+64/+1728 increment via HTMX, in-place update (row + OOB overall progress bar)
- [ ] -1/-64/-1728 decrement via same `edit-done` endpoint, clamped to 0
- [ ] `amount=0` returns 400
- [ ] Tapping count opens free-entry input; Enter/blur sets absolute value clamped to [0, required]; Escape reverts
- [ ] Completed resources: strikethrough, green bar, hidden counter buttons
- [ ] Each `.resource-row` has `data-item-name` and `data-progress-pct` attributes
- [ ] Search filters by item name client-side, no server round-trip
- [ ] Sort controls reorder by name A-Z or progress % client-side
- [ ] After HTMX counter swap, active search filter is re-applied
- [ ] "No resources match your search." shown on zero results
- [ ] Task checklist with working check/uncheck toggle
- [ ] "+ Add task" inline flow (Enter submits, Escape/empty blur cancels)
- [ ] Toggle persists preference to DB via `detail-content` endpoint
- [ ] Plan preference renders "Plan view — Coming soon" stub with functional toggle
- [ ] `pageShell()` accepts `scripts` parameter and renders script tags
- [ ] Mobile: 64px min row height, 36px min button tap targets, stacked layout
- [ ] Mobile header: back arrow, project name centered, toggle right
- [ ] Desktop header: breadcrumb, project name, status badge, location, overall progress bar, toggle
- [ ] No resources empty state: muted text, search/sort hidden
- [ ] No tasks empty state: only "+ Add task" visible
- [ ] No hardcoded colors, no inline styles
- [ ] Counter update shows `.counter-flash` animation (1 → 1.1 → 1, 100ms)
- [ ] Progress bar animates on width change (200ms ease)
- [ ] Old `done-more` route removed
- [ ] Integration tests pass (all 8 cases listed in sub-task 12)

## Out of scope / deferred

| Item | Phase / Spec | Reason |
|------|-------------|--------|
| Plan view content | Feature 10 | Separate spec |
| Partial dependency notice | Phase 2 | Requires dependency resolution engine |
| Production path link | Phase 2 | Requires path generation |
| Dependencies section | Phase 2 | Requires dependency graph |
| Inline resource definition | Phase 2 | Plan view feature |
| Location editing | Separate spec | Not part of execute view core |
| `/app/projects/:projectId` redirect | Feature 13 | Cleanup task |
| `ResourcesTab.kt` `/done-more` reference | Feature 13 | Legacy UI removal |
| DAG visualisation | Phase 3 | |

## Tech lead review
Verdict: approved (changes recommended — incorporated)
Notes: scripts param on pageShell, detail-content endpoint for toggle, edit-done validation and SQL specifics, OOB swap target ID alignment, symmetric counter buttons (-1728/-64/-1/+1/+64/+1728) with data attributes, htmx:afterSwap re-filter.
