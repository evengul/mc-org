---
linear-issue: MCO-153
status: approved
phase: 1
created: 2026-03-13
---

# Feature 10b: Project Detail — Resource Detail Panel

## Summary
Add a resource detail panel to the plan view — a `<dialog>` element that functions as a slide-over panel on desktop (400-480px, from right) and a full-screen bottom sheet on mobile. The panel shows the resource header with editable quantity, a source section where the user can set "Manual gather" or link an existing project, and a remove action with confirmation. This spec also includes the database migration for `source_type` and `solved_by_project_id`, the domain model update, and making status dots functional (green when source is set, grey when not). Depends on MCO-139 (plan view resource table).

## User value
**Personas served:** Technical player (primary).

**What the user does differently:** The technical player clicks a resource row in the plan view table and a panel slides in showing that resource's details. From the panel, they set the source — either "Manual gather" (going to mine it themselves) or select an existing project in the same world that produces this resource. The status dot in the table updates to green immediately. They can also edit the required quantity or remove the resource entirely from the panel. Without this feature, the user can see resources in the table (MCO-139) but cannot assign sources — the status dots are permanently grey and the source column always shows "--".

**What happens if we don't build it:** Plan view is read-only for source assignment. The `source_type` column added in this spec has no UI to set it. The technical player cannot complete the planning workflow without falling back to legacy UI (which is being removed).

## Scope

### Included
- Database migration: `source_type VARCHAR(20)` and `solved_by_project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL` on `resource_gathering`
- Domain model update: `sourceType: String?` on `ResourceGatheringItem`
- Update `toResourceGatheringItem()` extractor to read `source_type` and joined project id/name
- Update `GetAllResourceGatheringItemsStep` and `GetResourceGatheringItemStep` to include new columns and LEFT JOIN
- Status dots become functional: green when `source_type IS NOT NULL`, grey when NULL
- Source badge becomes functional: shows "Manual gather", project name, or "--"
- Resource detail panel using `<dialog>` element: desktop slide-over (400-480px), mobile bottom sheet
- Panel header: item name (read-only), required quantity (editable inline)
- Panel source section: "Not set" state (Manual gather + Use existing project buttons), "Set" state (badge + Change action)
- Panel footer: "Remove resource" danger button with confirmation
- Panel close: Escape (free via `<dialog>`), click outside (desktop), back arrow, X button
- Row click in plan view table opens panel
- Clicking different row swaps panel content; clicking same row closes panel
- Panel closes on view toggle (plan to execute)
- OOB swaps using `hxOutOfBands()` helper for table row status dot and source badge updates
- New endpoints: `GET .../detail-panel`, `PATCH .../source`, `DELETE .../source`
- New step: `GetProjectsInWorldStep` — `SELECT id, name FROM projects WHERE world_id = ? AND id != ? ORDER BY name`

### Excluded
- Item name editing in panel (name comes from Minecraft data; low value)
- Crafting/smelting source options (Phase 2 — requires resource graph scorer)
- Dependency tree display in panel (Phase 2)
- "Idea Hub matches" source option (Phase 2)
- Production path linking (Phase 2)
- Auto-resolved indicator (Phase 2)
- Bulk operations (Phase 2)

## Behaviour

### Opening the panel
- User clicks a resource row in the plan view table (anywhere on the row except the delete button and qty edit input)
- Click triggers HTMX GET: `GET /worlds/:worldId/projects/:projectId/resources/gathering/:id/detail-panel`
- Response is an HTML fragment rendered into `#resource-panel` — a `<dialog>` element placed in `projectDetailPage()` after the `main {}` block
- Desktop: panel appears as a right-aligned slide-over, 400-480px wide, with backdrop
- Mobile: panel appears as a full-screen bottom sheet
- The `<dialog>` is opened via `showModal()` from JS after HTMX swaps the content in

### Panel layout (desktop)
```
+------------------------------------------+
|  <- Back                           [X]   |
|                                          |
|  Iron Ingot                              |
|  Required: [ 64 ]  (click to edit)      |
|                                          |
|  --- Source ---                           |
|                                          |
|  [state A: Not set]                      |
|  No source selected                      |
|                                          |
|  [Manual gather]                         |
|  [Use from: select project v]            |
|                                          |
|  [state B: Set]                          |
|  ● Manual gather              [Change]  |
|  OR                                      |
|  ● Iron Farm (project)        [Change]  |
|                                          |
|                                          |
|  ─────────────────────────────────────── |
|  [Remove resource]                       |
+------------------------------------------+
```

### Panel layout (mobile)
```
+------------------------------------------+
|  <- Back                           [X]   |
|                                          |
|  Iron Ingot                              |
|  Required: [ 64 ]                        |
|                                          |
|  --- Source ──────────────────────        |
|                                          |
|  (same as desktop)                       |
|                                          |
|                                          |
|                                          |
|  ─────────────────────────────────────── |
|  [Remove resource]                       |
+------------------------------------------+
```

### Source section — "Not set" state
- Displayed when `source_type IS NULL`
- Shows "No source selected" in muted text
- Below that, action buttons stacked vertically:
  - **"Manual gather"** — `.btn--secondary`, full width in panel
    - HTMX PATCH to `PATCH .../source` with body `type=manual`
    - Server sets `resource_gathering.source_type = 'manual'`, `solved_by_project_id = NULL`
    - Response: updated panel source section (now shows "Set" state) + OOB swap via `hxOutOfBands()` for table row `#plan-row-{id}` (status dot grey to green, source badge "--" to "Manual gather")
  - **"Use existing project"** — `.btn--secondary`, full width, only shown if other projects exist in the same world
    - Reveals a searchable select dropdown of projects in the same world (excluding current project)
    - Options show project name. Selecting one triggers HTMX PATCH to `PATCH .../source` with body `type=project&projectId=X`
    - Server sets `resource_gathering.source_type = 'project'` and `resource_gathering.solved_by_project_id = X`
    - Response: same OOB pattern — status dot green, source badge shows project name

### Source section — "Set" state
- Displayed when `source_type IS NOT NULL`
- Shows source as a labelled badge:
  - If `source_type = 'manual'`: green dot + "Manual gather" text
  - If `source_type = 'project'`: green dot + project name (from LEFT JOIN), optionally as a link to that project
- **"Change"** action button (`.btn--ghost`, right-aligned):
  - HTMX DELETE to `DELETE .../source`
  - Server sets `resource_gathering.source_type = NULL` and `resource_gathering.solved_by_project_id = NULL`
  - Response: updated panel source section (reverts to "Not set" state) + OOB swap via `hxOutOfBands()` for table row (status dot green to grey, source badge to "--")

### Quantity editing in panel
- Required quantity displayed as a clickable value next to "Required:" label
- Same inline edit pattern as table qty edit from MCO-139
- Uses the same `PATCH .../required` endpoint from MCO-139
- Response: returns updated panel qty display. The plan table row also needs updating — either via OOB swap for `#plan-row-{id}` qty cell, or by returning the full updated row

### Remove resource from panel
- "Remove resource" button in panel footer, styled as `.btn--danger`
- Click opens confirmation using existing `confirmDeleteModal` pattern: title "Remove [Item Name]", description "[Item Name] and its source assignment will be permanently removed."
- On confirm: HTMX DELETE to existing `DELETE .../resources/gathering/:id?context=plan`
- Panel closes (JS calls `dialog.close()`), table row removed via OOB swap (`hxOutOfBands("delete:#plan-row-{id}")`), empty state shown if last resource
- The existing delete handler from MCO-139 handles the `?context=plan` response; the panel JS handles closing the dialog

### Closing the panel
- **Escape key**: free via `<dialog>` element's native behaviour. Listen to `cancel` event on the dialog to clean up state
- **Click outside** (desktop): `<dialog>` with `showModal()` provides a `::backdrop` pseudo-element. Click on backdrop triggers close via `onclick` handler that checks `event.target === dialog`
- **Back arrow / X button**: button in panel header calls `dialog.close()`
- **View toggle**: when user toggles to execute view, JS detects the `htmx:afterSwap` on `#project-content` and calls `dialog.close()` if the dialog is open
- After close: dialog content is cleared to avoid stale state on next open

### Click different row while panel open
- If panel is already showing resource A and user clicks resource B's row:
  - HTMX GET fetches resource B's panel content
  - Panel content swaps in-place within the `<dialog>` (no close/reopen — just content swap)
- If the same row is clicked: panel closes (toggle behaviour, handled in JS by comparing `data-resource-id` of the click target with the currently displayed resource)

### Panel container placement
- `#resource-panel` is a `<dialog>` element placed in `projectDetailPage()` after the `main {}` block, inside the `pageShell()` body
- Not placed inside `<main>` or `#project-content` — this ensures the panel survives toggle content swaps
- The dialog uses `showModal()` for proper focus trapping and backdrop

### Status dots update (table rows)
- With the migration and domain model changes in this spec, plan view table rows from MCO-139 now reflect actual source status:
  - Green (`.status-dot--set`): `source_type IS NOT NULL` (either 'manual' or 'project')
  - Grey (`.status-dot--unset`): `source_type IS NULL`
- Source badge column also becomes functional:
  - "Manual gather" when `source_type = 'manual'`
  - Project name when `source_type = 'project'` (from `solvedByProject` populated via LEFT JOIN)
  - "--" when `source_type IS NULL`
- The `planResourceRow()` function from MCO-139 is updated to read `sourceType` and `solvedByProject` from the domain model

### Edge cases
- Panel open + toggle to execute: panel closes (JS handles `htmx:afterSwap` on `#project-content`)
- Panel open + browser back: `<dialog>` close event fires, normal page navigation proceeds
- "Use existing project" with no other projects in world: button not shown (the `GET .../detail-panel` response omits it when `GetProjectsInWorldStep` returns empty list)
- Set source to project that is later deleted: `ON DELETE SET NULL` on the FK sets `solved_by_project_id` to NULL. The `source_type` remains 'project' but with no linked project. The panel shows "Unknown project" and the user can "Change" to clear it. The query LEFT JOIN handles the NULL gracefully
- Remove resource via panel while table has pending inline edit on another row: independent operations, no conflict
- Panel open on mobile + orientation change: CSS media query handles layout shift
- Resource not found when opening panel (race condition — deleted by another tab): endpoint returns 404, JS shows brief error and does not open panel
- Clicking delete button on table row while panel is open for that row: delete proceeds, panel closes on `htmx:afterSwap`

## Technical approach

### Modules touched
- **mc-web** (primary): new template, new pipeline steps, new endpoints, new CSS, new JS, migration
- **mc-domain**: add `sourceType: String?` to `ResourceGatheringItem`

### Database changes
- Migration `V2_33_0__add_source_columns_to_resource_gathering.sql`:
  ```sql
  ALTER TABLE resource_gathering
      ADD COLUMN source_type VARCHAR(20),
      ADD COLUMN solved_by_project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL;

  CREATE INDEX idx_rg_solved_by_project ON resource_gathering(solved_by_project_id);
  ```
- `source_type` values: NULL (not set), 'manual' (manual gather), 'project' (solved by another project)
- `solved_by_project_id` is only set when `source_type = 'project'`
- `ON DELETE SET NULL` ensures deleting a linked project clears the FK without cascading to the resource

### Domain model changes
- `ResourceGatheringItem` in mc-domain: add `sourceType: String? = null` field
- The existing `solvedByProject: Pair<Int, String>?` continues to be populated, now via LEFT JOIN on `projects` using `solved_by_project_id`

### Extractor changes
- `toResourceGatheringItem()`: read `source_type` column, read `solved_by_project_id` and joined `project_name` to populate `solvedByProject`

### Query changes
- `GetAllResourceGatheringItemsStep`: add `source_type` to SELECT, add `LEFT JOIN projects p ON rg.solved_by_project_id = p.id` to populate project name, select `p.id` and `p.name`
- `GetResourceGatheringItemStep`: same LEFT JOIN addition

### New template files
- `ResourceDetailPanel.kt` — `FlowContent.resourceDetailPanel(resource, projectsInWorld)` rendering the `<dialog>` content: header (item name + editable qty), source section (not-set or set state based on `sourceType`), footer with remove button

### Modified template files
- `ProjectDetailPage.kt` — add `<dialog id="resource-panel">` after `main {}` block. Update `planResourceRow()` to use `sourceType` and `solvedByProject` for status dot color and source badge text. Add click handler wiring on table rows (JS delegation)

### New pipeline steps
- `SetResourceSourceStep` — updates `source_type` and optionally `solved_by_project_id` on `resource_gathering`
- `ClearResourceSourceStep` — sets `source_type = NULL` and `solved_by_project_id = NULL`
- `GetResourceForPanelStep` — fetches single resource with source info (reuses updated `GetResourceGatheringItemStep` with LEFT JOIN)
- `GetProjectsInWorldStep` — `SELECT id, name FROM projects WHERE world_id = ? AND id != ? ORDER BY name`. Simple dedicated step, does not reuse existing dependency infrastructure

### New endpoints
- `GET /worlds/:worldId/projects/:projectId/resources/gathering/:id/detail-panel` — fetch resource (with source info), fetch projects in world (for dropdown), render panel template. Returns HTML fragment for `#resource-panel` inner content
- `PATCH /worlds/:worldId/projects/:projectId/resources/gathering/:id/source` — body params: `type` (required: "manual" or "project"), `projectId` (required if type=project, must reference project in same world). Updates `source_type` and `solved_by_project_id`. Returns: updated panel source section HTML + OOB swap via `hxOutOfBands()` for plan table row `#plan-row-{id}` (status dot + source badge)
- `DELETE /worlds/:worldId/projects/:projectId/resources/gathering/:id/source` — clears `source_type` and `solved_by_project_id`. Returns: updated panel source section HTML + OOB swap via `hxOutOfBands()` for plan table row

### Routes
- All three endpoints registered in `WorldHandler.kt` under the existing `route("/{resourceGatheringId}")` block:
  - `get("/detail-panel") { call.handleGetResourceDetailPanel() }`
  - `patch("/source") { call.handleSetResourceSource() }`
  - `delete("/source") { call.handleClearResourceSource() }`

### CSS files
- New: `/static/styles/components/resource-panel.css`:
  - `dialog#resource-panel` — positioned fixed, right: 0, top: 0, height: 100vh, width: 440px, no default dialog centering
  - `dialog#resource-panel::backdrop` — background: rgba(0,0,0,0.5)
  - Open/close transitions via CSS (dialog `[open]` state)
  - Mobile media query: width: 100%, height: 100%, positioned at bottom
  - Panel sections: header, source, footer spacing per design tokens

### JS files
- New: `/static/scripts/resource-panel.js`:
  - Click delegation on plan table rows: extract `data-resource-id`, HTMX GET to detail-panel endpoint, swap into `#resource-panel`, call `dialog.showModal()`
  - Toggle behaviour: click same row calls `dialog.close()`
  - `cancel` event listener on dialog for Escape cleanup
  - Click on `::backdrop` — `dialog.onclick` checks `event.target === dialog` to close
  - Back/X button click handlers call `dialog.close()`
  - `htmx:afterSwap` listener on `#project-content` — close dialog on view toggle
  - Inline qty edit in panel (same pattern as `plan-view.js`)
  - Track currently displayed resource ID for toggle detection

### Skills to load
- `/docs-product`, `/docs-development`, `/docs-htmx`, `/add-endpoint`, `/add-migration`, `/add-step`, `/docs-testing`

## Sub-tasks

- [ ] 1. **Database migration + domain model** — Create `V2_33_0__add_source_columns_to_resource_gathering.sql` adding `source_type VARCHAR(20)` and `solved_by_project_id INTEGER REFERENCES projects(id) ON DELETE SET NULL` with index. Add `sourceType: String? = null` to `ResourceGatheringItem` in mc-domain. Update `toResourceGatheringItem()` extractor to read `source_type` and joined project id/name. Update `GetAllResourceGatheringItemsStep` and `GetResourceGatheringItemStep` with LEFT JOIN on projects and `source_type` in SELECT.
- [ ] 2. **Update plan view table rows** — Update `planResourceRow()` in `ProjectDetailPage.kt`: status dot is green (`.status-dot--set`) when `resource.sourceType != null`, grey otherwise. Source badge shows "Manual gather" when `sourceType = "manual"`, project name when `sourceType = "project"` (from `solvedByProject`), "--" when null.
- [ ] 3. **Panel template** — Create `ResourceDetailPanel.kt` with `FlowContent.resourceDetailPanel(resource, projectsInWorld)`. Render header (item name, editable qty with "Required:" label), source section (not-set state: "No source selected" + Manual gather button + Use existing project button with searchable select; set state: source badge + Change button), footer (Remove resource `.btn--danger`). Use design tokens throughout.
- [ ] 4. **Panel container + row click wiring** — Add `<dialog id="resource-panel">` to `projectDetailPage()` after `main {}`. CSS in `resource-panel.css` for dialog positioning (right slide-over desktop, bottom sheet mobile, backdrop). Add `resource-panel.js` to page scripts list.
- [ ] 5. **GetProjectsInWorldStep** — New step: `SELECT id, name FROM projects WHERE world_id = ? AND id != ? ORDER BY name`. Returns `List<Pair<Int, String>>` (id, name). Unit test for the step.
- [ ] 6. **Panel endpoint** — `GET .../detail-panel`. Pipeline: fetch resource by ID (reuse `GetResourceGatheringItemStep`), fetch projects in world via `GetProjectsInWorldStep`, render `resourceDetailPanel()`. Register route in `WorldHandler.kt`. Return HTML fragment.
- [ ] 7. **Set source endpoint** — `PATCH .../source`. Validate `type` param ("manual" or "project"). If "project", validate `projectId` param exists and refers to a project in the same world. `SetResourceSourceStep` updates `source_type` and `solved_by_project_id`. Return: updated panel source section HTML + OOB swap via `hxOutOfBands()` for `#plan-row-{id}`.
- [ ] 8. **Clear source endpoint** — `DELETE .../source`. `ClearResourceSourceStep` sets both columns to NULL. Return: updated panel source section HTML + OOB swap via `hxOutOfBands()` for `#plan-row-{id}`.
- [ ] 9. **Remove resource from panel** — Wire "Remove resource" button to existing `DELETE .../resources/gathering/:id?context=plan` with `hxDeleteWithConfirm`. Panel JS closes dialog on successful delete (listen for `htmx:afterRequest` on the confirm button).
- [ ] 10. **Panel JS** — `resource-panel.js`: row click delegation (HTMX GET + `showModal()`), toggle (same row = close), `cancel` event cleanup, backdrop click close, back/X close, `htmx:afterSwap` on `#project-content` closes dialog, track current resource ID, inline qty edit in panel.
- [ ] 11. **Integration tests** — `GET .../detail-panel` returns panel HTML for valid resource. `GET .../detail-panel` returns 404 for non-existent resource. `PATCH .../source` with `type=manual` sets source, returns OOB swap. `PATCH .../source` with `type=project&projectId=X` sets source and links project. `PATCH .../source` with invalid type returns 400. `PATCH .../source` with `type=project` and projectId from different world returns 400. `DELETE .../source` clears source. Status dots in plan view table reflect source state after migration. Unit test for `GetProjectsInWorldStep`. Unit test for `SetResourceSourceStep`. Unit test for `ClearResourceSourceStep`.

## Acceptance criteria

- [ ] Migration adds `source_type` and `solved_by_project_id` columns to `resource_gathering`
- [ ] `solved_by_project_id` FK uses `ON DELETE SET NULL`
- [ ] `ResourceGatheringItem` domain model includes `sourceType: String?`
- [ ] `toResourceGatheringItem()` reads `source_type` and joined project id/name
- [ ] `GetAllResourceGatheringItemsStep` includes LEFT JOIN on projects and `source_type` in SELECT
- [ ] Plan view table status dots are green when `source_type IS NOT NULL`, grey when NULL
- [ ] Plan view table source badges show "Manual gather", project name, or "--" based on source state
- [ ] Clicking a resource row in plan view opens the detail panel (`<dialog>` with `showModal()`)
- [ ] Desktop: panel positioned right, 400-480px wide, with backdrop
- [ ] Mobile: panel positioned as full-screen bottom sheet
- [ ] Panel header shows item name and editable required quantity
- [ ] Clicking qty in panel opens inline edit; Enter/blur saves via PATCH .../required; Escape reverts
- [ ] Source "Not set": shows "Manual gather" button; shows "Use existing project" if other projects exist
- [ ] Clicking "Manual gather" sets `source_type = 'manual'`; status dot turns green; source badge shows "Manual gather"
- [ ] Selecting a project sets `source_type = 'project'` and `solved_by_project_id`; status dot turns green; source badge shows project name
- [ ] Source "Set": shows current source with "Change" action
- [ ] Clicking "Change" clears `source_type` and `solved_by_project_id`; status dot turns grey; source badge shows "--"
- [ ] OOB swaps use `hxOutOfBands()` helper
- [ ] "Remove resource" shows confirmation modal; on confirm, resource deleted, panel closes, table row removed
- [ ] Deleting last resource from panel transitions table to empty state
- [ ] Panel closes on: Escape (native `<dialog>`), click outside (backdrop), back arrow, X button
- [ ] Panel closes when toggling to execute view
- [ ] Clicking different row swaps panel content without close/reopen
- [ ] Clicking same row closes the panel (toggle behaviour)
- [ ] "Use existing project" not shown when no other projects exist in world
- [ ] `GetProjectsInWorldStep` returns projects in same world excluding current, ordered by name
- [ ] No inline styles; design tokens used throughout
- [ ] Unit tests pass for `GetProjectsInWorldStep`, `SetResourceSourceStep`, `ClearResourceSourceStep`
- [ ] Integration tests pass for all panel endpoints
- [ ] `mvn clean compile` passes; `mvn test` passes

## Out of scope / deferred

| Item | Phase / Spec | Reason |
|------|-------------|--------|
| Item name editing in panel | Future enhancement | Name comes from Minecraft data; editing is edge-case |
| Crafting/smelting source picker | Phase 2 | Requires resource graph scorer integration |
| Dependency tree in panel | Phase 2 | Requires production path resolution |
| "Idea Hub matches" source option | Phase 2 | Requires cross-referencing idea items with resource needs |
| Production path linking | Phase 2 | Requires path generation engine |
| Auto-resolved indicator | Phase 2 | Requires path confirmation workflow |
| Bulk source assignment | Phase 2 | Batch operations on multiple resources |
| Amber status dot | Phase 2 | Only meaningful with crafting tree partial resolution |

## Tech lead review
Verdict: Changes recommended — incorporated

Notes: Migration moved here from MCO-139 with both columns (`source_type` and `solved_by_project_id`). FK uses `ON DELETE SET NULL`. Domain model and extractor updates included. Panel uses `<dialog>` element (gets Escape for free, matches existing `confirmDeleteModal` pattern). Panel placed after `main {}` in `projectDetailPage()`, not in `pageShell`. `GetProjectsInWorldStep` is a new dedicated step with simple query, not reusing existing dependency infrastructure. OOB swaps use `hxOutOfBands()` helper throughout. Status dots become functional in this spec.
