---
linear-issue: MCO-139
status: approved
phase: 1
created: 2026-03-13
---

# Feature 10a: Project Detail — Plan View Resource Table

## Summary
Replace the plan view stub ("Coming soon") with a working plan view that shows a resource table with grey status dots, inline quantity editing, an "+ Add resource" flow, and a collapsed tasks section. This is the first half of the plan view — the table and all its CRUD operations. Source assignment and the resource detail panel are a separate follow-up spec (MCO-153). This serves technical players who need to define what a project requires before switching to execute view for gathering.

## User value
**Personas served:** Technical player (primary), Casual player (secondary — adding resources after importing an idea with none).

**What the user does differently:** The technical player opens plan view, sees all defined resources at a glance, adds new resources via an item picker, edits quantities inline, and removes resources they no longer need. Today the plan view is a dead stub. After this feature, planning and execution are cleanly separated: plan view for "what do I need", execute view for "how much have I gathered". This also lays the foundation for Phase 2 features (production paths, dependency resolution) which all build on the plan view resource table.

**What happens if we don't build it:** The plan/execute toggle is a broken promise. Technical players have no planning surface. Resource definition only exists in the legacy UI being removed in Feature 13. The entire Phase 2 roadmap has no foundation.

## Scope

### Included
- Plan view content replacing `planViewStub()` in `ProjectDetailPage.kt`
- Resource table with columns: Status dot (all grey) | Item | Qty | Source (all "--")
- Click qty cell for inline edit (new PATCH endpoint updates `required`)
- "+ Add resource" button with item search combo (reuses existing `GET /items/search` endpoint and item-search pattern from `DraftItemRequirementFields.kt`)
- Delete resource with confirmation (existing endpoint, new HTMX response for plan view)
- Context detection via `?context=plan` query param on HTMX requests from plan view
- Update `handleGetProject()` to load resources when view is "plan"
- Tasks section: present but collapsed by default, expandable
- Empty state: "No resources defined" + "Switch to Execute" and "Browse Idea Hub" CTAs
- Plan view fragment returned by existing `GET .../detail-content?view=plan` endpoint (updated)
- OOB toggle swap (reuses existing pattern)

### Excluded
- Resource detail panel — slide-over/bottom-sheet UI (MCO-153)
- Source assignment — set/clear source endpoints (MCO-153)
- Database migration for `source_type` column (MCO-153)
- Domain model changes for source (MCO-153)
- Green status dots (MCO-153 — all dots are grey in this spec)
- Search/filter controls (plan view resource lists are typically small)
- Amber status dot (Phase 2 — only meaningful with partial crafting tree resolution)
- "Generate path" / "View path" CTAs (Phase 2 — no path generation engine in Phase 1)
- Dependencies section: blocked-by / blocks (Phase 2 — old `project_dependency` table dropped in V2_0_0; needs redesign)
- Crafting/smelting source options (Phase 2)
- Inline resource definition from free text (Phase 2 — "Inline Resource Planning" epic)

## Behaviour

### Plan view layout (desktop)
```
[Logo]   Worlds > [World Name] > [Project Name]     Ideas   gear

[Project Name]                              [PLAN | EXEC]
[Status badge]  Location: X:120, Z:-430

--- RESOURCES ---------------------------------------------------

[+ Add resource]

  Status | Item           | Qty    | Source
  ●grey  | Iron Ingot     | 64     | --
  ●grey  | Redstone Dust  | 128    | --
  ●grey  | Oak Planks     | 16     | --

--- TASKS (collapsed) ------------------------------------------
  > Tasks (3)   [click to expand]
```

### Plan view layout (mobile)
```
<-  [Project Name]          [PLAN|EXEC]

[Status badge]

--- RESOURCES --------------------

[+ Add resource]

  ● Iron Ingot          128   --
  ● Redstone Dust        64   --
  ● Oak Planks           16   --

--- TASKS (collapsed) -----------
  > Tasks (3)
```

### Resource table behaviour
- Table uses `.data-table` class from design system
- Each row is a `<tr>` in a `<table>` — this is a data table, not a card list
- Rows returned in default server order (no sort changes to existing queries)
- Status dot column: small circle element, always grey (`--text-disabled`) in this spec. Source assignment arrives in MCO-153
- Item column: item name, plain text
- Qty column: displays required amount. Clicking opens an inline `<input type="number" min="1">` pre-filled with current value. On blur or Enter: HTMX PATCH to `PATCH .../required` updates the `required` column and returns the updated row. On Escape: reverts without submitting
- Source column: always shows "--" (em dash in muted text) in this spec. Source display becomes functional in MCO-153
- Rows with `required = 0` are filtered out (same as execute view)
- Row click behaviour: in this spec, clicking a row does nothing beyond the qty inline edit. MCO-153 will add panel opening on row click. The rows include `data-resource-id` attributes to support future panel wiring
- Each row has an id of `plan-row-{resourceGatheringId}` for OOB targeting

### "+ Add resource" flow
- Button positioned above the resource table, styled as `.btn--secondary`
- Clicking reveals an inline form above the table:
  - Item search: reuses the existing `GET /items/search?q=...` endpoint and item-search-combo pattern from `DraftItemRequirementFields.kt`. Text input with HTMX GET triggering search results dropdown. Selecting an item sets a hidden `itemId` field
  - Quantity: `<input type="number" min="1" value="1">`, compact width
  - Submit button: "Add" (`.btn--primary .btn--sm`)
  - Cancel: "X" button or Escape closes the form
- HTMX POST to existing `POST /worlds/:worldId/projects/:projectId/resources/gathering?context=plan`
- The `?context=plan` query param causes the handler's `onSuccess` to return a plan-view `<tr>` row instead of the old template's `<li>` element
- After creation: new row appears in table, form resets
- OOB swap removes the empty state element if it was showing
- Duplicate item: not prevented in Phase 1 (matches existing behaviour)

### Delete resource
- Each row includes a delete button (small "x", right-aligned in the row)
- Uses existing `hxDeleteWithConfirm` pattern (same as task delete)
- Calls existing `DELETE /worlds/:worldId/projects/:projectId/resources/gathering/:id?context=plan`
- The `?context=plan` query param causes the handler to return plan-view-compatible OOB swaps
- On delete: row removed from table via `hx-swap="delete"` targeting `#plan-row-{id}`. If last resource removed, return OOB swap to show empty state

### Inline qty edit behaviour
- Clicking the qty cell replaces the text with `<input type="number" min="1">` pre-filled with current required value
- Implemented client-side: JS click handler on `.plan-resource-table__qty` cells
- On Enter or blur: HTMX PATCH to `PATCH .../required` with `value={entered value}`
- Server validates: value must be integer >= 1. Returns 400 on invalid input
- Server updates: `UPDATE resource_gathering SET required = ? WHERE id = ?` with value clamped to minimum 1
- Response: returns updated `<tr>` row (same format as table row) for `hx-swap="outerHTML"`
- On Escape: reverts to text display without submitting
- On blur with unchanged value: no submission, reverts to text

### handleGetProject update
- `GetProjectPipeline.handleGetProject()` currently only loads resources + tasks when view is "execute" (plan branch renders stub)
- Must be updated: when `view == "plan"`, also load resources and tasks, then render `planViewContent()` instead of `planViewStub()`
- Same data loading as execute branch — resources via `GetAllResourceGatheringItemsStep`, tasks via `SearchTasksStep`

### handleGetDetailContent update
- `GetDetailContentPipeline.handleGetDetailContent()` plan branch currently returns `planViewFragment()` (the stub)
- Must be updated: load resources + tasks (same as execute branch does), render `planViewContent()` fragment
- `planViewFragment()` function signature changes to accept `Project`, `List<ResourceGatheringItem>`, `List<ActionTask>`

### Tasks section (collapsed)
- Section header: clickable div with "Tasks (N)" where N is task count, styled with expand/collapse chevron
- Collapsed by default: task list content has `display: none` via a CSS class (`.tasks-section--collapsed`)
- Click header toggles the class — pure client-side JS, no HTMX request
- When expanded: renders same `taskList()` and `addTaskInline()` components from execute view
- Tasks are included in the initial plan view server render, just visually hidden when collapsed
- Collapse state is not persisted — resets on page load or toggle

### Empty states
- **No resources:** centered `.empty-state` with heading "No resources defined yet."
  - Two CTAs side by side:
    - "Switch to Execute" — `.btn--secondary`, triggers toggle to execute view (same `hxGet` as the EXEC toggle button)
    - "Browse Idea Hub" — `.btn--secondary`, links to `/ideas`
  - Tasks section still shown below (collapsed)
  - "+ Add resource" button still shown above the empty state
  - Empty state element has id `plan-empty-state` for OOB removal when first resource is added
- **No tasks:** when tasks section is expanded, shows "No tasks yet." with "+ Add task" button (same as execute view)
- **No resources AND no tasks:** both empty states stacked

### Edge cases
- Resource with `required = 0`: filtered out, not rendered
- Editing qty to 0 or negative: HTML `min="1"` prevents on client; server validates and returns 400
- Editing qty to same value: on blur, JS detects no change and reverts without HTMX call
- Toggle to execute and back: plan view content is re-fetched via `GET .../detail-content?view=plan`, ensuring fresh data
- Delete while inline edit is active on same row: delete takes precedence, row removed
- Create resource when table was in empty state: empty state element removed via OOB swap, new row appears

## Technical approach

### Modules touched
- **mc-web** (primary): templates, pipelines, handlers, routes, CSS, JS
- **mc-domain**: no changes

### New/modified template files
- `ProjectDetailPage.kt` — replace `planViewStub()` with `planViewContent()`. New functions: `planViewContent(project, resources, tasks)`, `planResourceTable(worldId, projectId, resources)`, `planResourceRow(worldId, projectId, resource)`. Update `planViewFragment()` signature to accept data params and render actual content
- Possibly extract plan view functions to `PlanViewContent.kt` if `ProjectDetailPage.kt` becomes too large

### New/modified pipeline steps
- New: `UpdateResourceRequiredAmountStep` — `UPDATE resource_gathering SET required = ? WHERE id = ?` with input validation (value >= 1)

### Modified handlers
- `GetProjectPipeline.handleGetProject()` — load resources + tasks when `view == "plan"`, render `planViewContent()`
- `GetDetailContentPipeline.handleGetDetailContent()` — plan branch loads resources + tasks, renders `planViewContent()` fragment
- `CreateResourceGatheringItemPipeline.handleCreateResourceGatheringItem()` — read `context` query param; if `context=plan`, return plan-view `<tr>` row in `onSuccess` + OOB removal of `#plan-empty-state`; if absent/other, return existing `<li>` response
- `DeleteResourceGatheringItemPipeline.handleDeleteResourceGatheringItem()` — read `context` query param; if `context=plan`, return empty body (row removed via `hx-swap="delete"` on the triggering element) + OOB swap to show `#plan-empty-state` if resource count reaches 0; if absent/other, return existing OOB progress response

### New endpoints
- `PATCH /worlds/:worldId/projects/:projectId/resources/gathering/:id/required` — validates `value` param (integer >= 1), updates `resource_gathering.required`, returns updated plan-view `<tr>` row

### Modified endpoint responses
- `POST .../resources/gathering?context=plan` — returns `<tr>` instead of `<li>`
- `DELETE .../resources/gathering/:id?context=plan` — returns view-appropriate OOB swaps

### Existing endpoints reused
- `GET /items/search?q=...` — item search for the "+ Add resource" item picker (existing, used in idea creation)

### Routes
- Register `PATCH .../required` in `WorldHandler.kt` under the existing `route("/{resourceGatheringId}")` block (alongside `edit-done` and `collected`)

### CSS files
- Modified: `/static/styles/pages/project-detail.css` — plan view table styles, collapsed tasks section, empty state layout
- Plan resource table styles: `.plan-resource-table` using `.data-table` base, status dot column, qty clickable cell
- Status dot styles: `.status-dot`, `.status-dot--unset` (grey) — `.status-dot--set` (green) defined here but unused until MCO-151
- Collapsed tasks: `.tasks-section--collapsed` hides task list content

### JS files
- New: `/static/scripts/plan-view.js` — inline qty edit (click to input, Enter/blur submits via HTMX, Escape reverts, unchanged-blur no-ops), tasks section expand/collapse toggle, "+ Add resource" form show/hide, item search selection handler (adapts pattern from `DraftItemRequirementFields.kt`)
- Loaded via `scripts` param on `pageShell()` (pattern established in MCO-138)

### Database changes
- None

### Skills to load
- `/docs-product`, `/docs-development`, `/docs-htmx`, `/add-endpoint`, `/add-step`, `/docs-testing`

## Sub-tasks

- [ ] 1. **Plan view resource table template** — Replace `planViewStub()` with `planViewContent(project, resources, tasks)`. Implement `planResourceTable()` rendering `.data-table` with grey status dot, item name, qty (clickable), source badge (all "--"), delete button per row. Row ids: `plan-row-{id}`. Include `data-resource-id` attributes on rows. Include collapsed tasks section with expand/collapse header. Include empty state (`#plan-empty-state`) with CTAs. Update `planViewFragment()` signature to accept data.
- [ ] 2. **Update data loading for plan view** — Modify `handleGetProject()` in `GetProjectPipeline` to load resources + tasks when `view == "plan"` and render `planViewContent()`. Modify `handleGetDetailContent()` plan branch to load resources + tasks and render `planViewContent()` fragment instead of stub.
- [ ] 3. **Update required amount endpoint** — New `UpdateResourceRequiredAmountStep`. New route `PATCH .../resources/gathering/:id/required` registered in `WorldHandler.kt`. Pipeline: validate `value` is integer >= 1 (return 400 otherwise), update `resource_gathering.required`, return updated plan-view `<tr>` row.
- [ ] 4. **Add resource (plan view variant)** — Reuse existing `GET /items/search?q=...` endpoint with the item-search-combo pattern from `DraftItemRequirementFields.kt` for item selection. Read `?context=plan` query param in `handleCreateResourceGatheringItem()`. Branch `onSuccess`: if plan context, return plan-view `<tr>` row + OOB removal of `#plan-empty-state`; otherwise return existing `<li>` response. Wire "+ Add resource" button with item search combo + qty input form, HTMX POST with `?context=plan`.
- [ ] 5. **Delete resource (plan view variant)** — Read `?context=plan` query param in `handleDeleteResourceGatheringItem()`. Branch `onSuccess`: if plan context, return empty body (row removed via `hx-swap="delete"` on the triggering element) + OOB swap to show `#plan-empty-state` if resource count reaches 0; otherwise return existing OOB progress response. Wire delete button on each row with `hxDeleteWithConfirm` targeting `#plan-row-{id}`.
- [ ] 6. **Plan view JS** — Create `/static/scripts/plan-view.js`: inline qty edit (click `.plan-resource-table__qty` to show input, Enter/blur triggers HTMX PATCH, Escape reverts, unchanged-blur no-ops), tasks section expand/collapse toggle on header click, "+ Add resource" form show/hide, item search selection handler (adapts `selectSearchedItem` pattern).
- [ ] 7. **CSS** — Status dot styles (`.status-dot--unset` grey, `.status-dot--set` green defined but unused). Plan resource table styles in `project-detail.css`. Collapsed tasks section (`.tasks-section--collapsed`). Add resource form styles. Ensure table is responsive on mobile (stack columns or horizontal scroll).
- [ ] 8. **Tests** — Unit test for `UpdateResourceRequiredAmountStep` (success path, value clamped to >= 1). Integration tests: `PATCH .../required` success (returns updated row), `PATCH .../required` with value=0 (returns 400), `PATCH .../required` auth failure (returns 403). Plan view create resource with `?context=plan` returns `<tr>`. Plan view delete resource with `?context=plan` removes row. `GET .../detail-content?view=plan` returns plan view content (not stub). `handleGetProject` with plan preference renders plan view with resource table.

## Acceptance criteria

- [ ] Plan view renders resource table when toggle is set to "plan" (no more "Coming soon" stub)
- [ ] Resource table shows grey status dot, item name, required quantity, and "--" source badge for each resource
- [ ] All status dots are grey (source assignment is MCO-151)
- [ ] All source badges show "--" (source display is MCO-151)
- [ ] Rows include `data-resource-id` and `id="plan-row-{id}"` attributes
- [ ] `handleGetProject()` loads resources when view is "plan"
- [ ] `handleGetDetailContent()` plan branch loads resources + tasks and returns actual plan view content
- [ ] Clicking qty in table opens inline number input; Enter/blur saves; Escape reverts
- [ ] `PATCH .../required` validates value >= 1, returns updated `<tr>` row
- [ ] `PATCH .../required` with value 0 or negative returns 400
- [ ] `PATCH .../required` without auth returns 403
- [ ] "+ Add resource" reveals form with item search combo (using existing `GET /items/search` endpoint) + quantity input
- [ ] Submitting form with `?context=plan` creates resource and adds `<tr>` row to table
- [ ] Submitting form without context param still returns `<li>` (existing behaviour preserved)
- [ ] Delete button on each row triggers confirmation, then removes row via `hx-swap="delete"`
- [ ] Delete with `?context=plan` returns plan-appropriate response
- [ ] Deleting last resource transitions to empty state
- [ ] Empty state shows "No resources defined yet." with "Switch to Execute" and "Browse Idea Hub" CTAs
- [ ] "+ Add resource" button visible even in empty state
- [ ] Tasks section collapsed by default; click header to expand
- [ ] Expanded tasks show same task list and "+ Add task" as execute view
- [ ] No inline styles; design tokens used throughout
- [ ] Mobile: table is usable (readable columns, adequate tap targets)
- [ ] Toggle from plan to execute and back re-fetches fresh data
- [ ] Unit test for `UpdateResourceRequiredAmountStep` passes
- [ ] Integration tests pass for all endpoint behaviours listed in sub-task 8
- [ ] `mvn clean compile` passes; `mvn test` passes

## Out of scope / deferred

| Item | Phase / Spec | Reason |
|------|-------------|--------|
| Resource detail panel (slide-over / bottom sheet) | MCO-153 | Separate spec, depends on this one |
| Source assignment (set/clear source) | MCO-153 | Panel-driven interaction |
| Green status dots | MCO-153 | Requires source_type column and assignment UI |
| `source_type` / `solved_by_project_id` migration | MCO-153 | Migration lives with the feature that uses it |
| Domain model changes for source | MCO-153 | Same reason |
| Row click opens panel | MCO-153 | No panel to open yet |
| Search/filter in plan view | Future enhancement | Resource lists during planning are typically small |
| Amber status dot (partially resolved) | Phase 2 | Only meaningful with crafting tree partial resolution |
| "Generate path" / "View path" CTAs | Phase 2 | No path generation engine |
| Dependencies section (blocked-by / blocks) | Phase 2 | Needs complete redesign |
| Crafting/smelting source options | Phase 2 | Requires scorer integration |
| Inline resource definition from text | Phase 2 | "Inline Resource Planning" epic |
| DAG visualisation | Phase 3 | |

## Tech lead review
Verdict: Changes recommended — incorporated

Notes: Removed migration and domain model changes (moved to MCO-153). All status dots grey, all source badges "--". Existing query order unchanged. Added explicit `handleGetProject()` update requirement for plan view data loading. Create/delete context detection via `?context=plan` query param with branching in `onSuccess`. Reuses existing `GET /items/search` endpoint for item picker. Tests specified: unit test for `UpdateResourceRequiredAmountStep`, integration tests for PATCH required (success, validation, auth), plan-view create/delete response variants.
