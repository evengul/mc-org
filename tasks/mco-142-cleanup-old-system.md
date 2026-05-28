---
linear-issue: MCO-142
parent-epic: MCO-128
status: approved
phase: 1
type: feature
created: 2026-05-08
sub-issues:
  - MCO-159  # Hoist common patterns (was 142a)
  - MCO-162  # Delete dead project routes + legacy handler/template tree (added during 159 audit)
  - MCO-160  # Delete legacy template system (was 142b; now blocked by 162)
  - MCO-161  # Delete legacy CSS + final sweep (was 142c)
---

# Feature 13: Delete old system + hoist common patterns

## Summary

Final cleanup pass for the MCO-128 frontend rewrite. Two parts: (1) delete the old `Component`/`LeafComponent`/`NodeComponent` hierarchy, the `createPage()` shell, the overworld/nether/end theme system, the `/app/` route prefix, all legacy CSS files written against the dead `--clr-*` / `--spacing-*` token set, and the Roboto Mono Google Fonts plumbing; (2) hoist the patterns the Layer 2 page rewrites have been inlining into proper shared component CSS and DSL helpers, so the codebase ends with one token system, one page shell, one component vocabulary.

Split into three Linear sub-issues that merge independently in order: 142a → 142b → 142c.

## Decisions locked

- Split into 142a / 142b / 142c sub-issues
- `/app/` route prefix: DELETE entirely (no users to migrate)
- `/test/page`: DELETE (dev-only, blocks Component deletion)
- DSL helper rule: ≥2 consumers required (Avatar acceptable: members + invitations)
- Test class-name updates: same commit as hoist (don't preserve old classes)
- DSL helpers verified via existing page integration tests; no new pure-DSL unit tests required
- CSS load-order: extend `pageShell()` hardcoded list for everywhere components (alert, badge, form-field); pass via `stylesheets` parameter for page-scoped (danger-zone, person-row)

## User value

- One token system, one component vocabulary — `grep --clr-` returns zero hits permanently
- Faster page work — hoisted patterns mean the next settings-style page is one DSL call, not 70 lines of inlined CSS
- Smaller surface to reason about — ~5,800 lines of legacy CSS and 84 template files drop
- Triple-font load and theme-flicker script gone — production payload shrinks; FOUC-prevention `<script>` deletable
- Unblocks Feature 14 (`docs-frontend`) — the skill documents the system as it is, not as it will be

## Scope (in)

### Part A — Deletion sweep

1. Delete `Component.kt`, `LeafComponent.kt`, `NodeComponent.kt`, `ComponentHelpers.kt` under `presentation/templated/common/component/`
2. Delete legacy template files under `templated/common/` once consumers are migrated:
   - `topbar/TopBar.kt`, legacy `layout/alert/Alert.kt`, `breadcrumb/BreadcrumbComponent.kt`, legacy `avatar/avatar.kt`, `chip/Chip.kt`, legacy `tabs/Tabs.kt`, legacy `modal/Modal*.kt`, `button/GenericButton.kt`, legacy `button/Buttons.kt` (NOT the DSL `templated/dsl/Buttons.kt`), `emptystate/EmptyState.kt`, `searchableselect/`, `searchField/`, `radiogroup/`, `Progress.kt`, `Icons.kt`
   - `link/Link.kt` rendering helpers (`linkComponent`, `actionLink`, `subtleLink`, `dangerLink`, `LinkComponent` class) — DELETE OUTRIGHT (3 callers, all in deleted files). Sealed interface `Link` is the only survivor; move to `templated/dsl/Link.kt`.
3. Delete legacy page shells: `templated/common/page/Page.kt`, `PageStyle.kt`, `PageScript.kt`, `templated/project/ProjectPage.kt`, `templated/world/WorldPage.kt` (both confirmed dead — `WorldHandler.kt:101-105` permanent-redirects `/worlds/{id}` to `/worlds/{id}/projects`; `handleGetProject` uses DSL `projectDetailPage`)
4. Delete `pipeline/world/GetWorldPipeline.kt` (orphaned by the redirect; `handleGetWorld()` no longer reached) and remove the import at `WorldHandler.kt:47`
5. Delete `templated/testpage/`, `pages/test-page.css`, and the `/test/page` route in `mainRouter.kt`
6. Port `app.mcorg.presentation.handler.handleAuth.handleGetSignOut` (`handleAuth.kt:32`) from `createPage` to `pageShell()` before `Page.kt` deletes
7. Port `confirmDeleteModal` to `templated/dsl/Modal.kt` BEFORE the legacy `templated/common/modal/ConfirmDeleteModal.kt` deletes:
   - Preserve `CONFIRM_DELETE_MODAL_ID` and all element IDs consumed by `hxDeleteWithConfirm` and `confirmation-modal.js`
   - Replace inline `style = "display: none;"` (lines 50, 67) with `is-hidden` utility class
   - Update `pageShell()` to call the new DSL modal
8. Delete legacy CSS:
   - `static/styles/root.css` (overworld/nether/end themes)
   - `static/styles/styles.css` (legacy import manifest)
   - `static/styles/utilities.css`, `static/styles/layout.css` (audit then delete)
   - All component CSS using dead tokens (~22 files: `avatar.css`, `chip.css`, `tabs.css` original, `common.css`, legacy `breadcrumb.css`, `notifications.css`, `roadmap.css`, `dialog.css`, `select.css`, `searchable-select.css`, `radio.css`, `checkbox.css`, `textarea.css`, `input.css`, `progress.css`, legacy `button.css`, `button-states.css`, `form-controls.css`, `body.css`, `main.css`, `navbar.css`, `a.css`, `icon.css`, `task-requirements.css`)
   - Page CSS for deleted pages (`world-page.css`, `project-page.css`, `idea-page.css`, `create-idea-page.css`, `ideas-page.css`, `test-page.css`)
9. Delete theme system: `static/scripts/theme-switcher.js`, `getThemeScript`, `[data-theme="..."]` blocks, `mc-org-theme` localStorage references
10. Remove Google Fonts: `<link>` tags + preconnect hints (implicit when `Page.kt` deletes; verify zero hits across templates)
11. Delete `route("/app") { ... }` block (lines 47-57) from `mainRouter.kt` entirely

### Part B — Hoist common patterns

| # | Pattern | Sources | Hoist target |
|---|---------|---------|--------------|
| B1 | Danger zone | settings, profile (copy-pasted byte-for-byte) | `components/danger-zone.css` rewrite. Existing DSL `dangerZone()` in `templated/dsl/DangerZone.kt`. Switch `DangerSection.kt` to use it. |
| B2 | Alert container (`#alert-container` fixed slide-in `<li>`) | settings | `components/alert.css` rewrite + new DSL `alertContainer()` in `templated/dsl/Alert.kt`. Migrate `SettingsPage.kt:51` away from legacy `alertContainer()` in the same commit. |
| B3 | Initials avatar (`.avatar`, `.avatar--md`) | settings (members + invitations) | new `templated/dsl/Avatar.kt` + `components/avatar.css` |
| B4 | Badge / chip (status pill variants) | settings, admin | Audit existing DSL `Badge.kt`, extend variants. New `components/badge.css` against real tokens. |
| B5 | Page heading (title + subtitle) | settings, profile, admin | new DSL `pageHeading()` in `templated/dsl/Layout.kt` |
| B6 | Settings section card | settings, profile, admin | new DSL `Section.kt` + `components/section.css` |
| B7 | Member / invitation row | settings | new DSL `PersonRow.kt` + `components/person-row.css` |
| B8 | Form field block (label + input + validation, includes B10 required indicator) | settings + every form | new DSL `FormField.kt` + extend `components/form.css` |
| B9 | `.subtle` utility class | various | single utility class in real-token utility CSS |
| B11 | Pill tabs (`.tabs__tab--*`) | settings | DSL `tabStrip(variant=PILLS)` exists; rewrite `components/tabs.css` against real tokens |
| B12 | Data-table mobile-as-cards (`@media max-width: 768px` reflow with `data-label` attrs) | admin | `components/data-table.css` rewrite (works with DSL `dataTable()`) |

### Part C — Token-reference sweep (final acceptance)

All seven greps return zero across `static/styles/` and `src/main/kotlin/`:

- `rg '\-\-clr-'`
- `rg '\-\-spacing-'`
- `rg '\-\-shadow-'`
- `rg '\-\-border-radius-'`
- `rg '\-\-text-(lg|xl|xxl|md|base-size)'`
- `rg '\-\-font-primary'`
- `rg '\[data-theme'`
- `rg 'mc-org-theme'` (additionally check `src/main/resources/`)

Plus:
- `rg 'createPage\(' src/main/` → 0
- `rg 'addComponent\(' src/main/` → 0
- `rg 'class .*: (Leaf|Node)?Component\b' src/main/kotlin/` → 0
- `rg 'fonts.googleapis|fonts.gstatic|Roboto.Mono' src/main/` → 0
- `rg 'SEARCHABLE_SELECT|searchable-select.js' src/main/` → 0

## Out of scope

- `docs-frontend` skill content (Feature 14 / MCO-143)
- Light theme (Phase 3)
- Lucide icon migration (separate epic)
- Mobile hamburger drawer JS (separate epic)
- `Link` sealed interface URL shape (already correct)
- Notification dispatch backend
- SuperAdmin world-deletion stub at `AdminHandler.kt:36-46`
- Any redesign of pages already shipped in Layer 2
- **Visible look-and-feel changes** — hoisting must not change pixel output. Markup may move into DSL helpers and classes may be renamed, but the rendered HTML+CSS produces the same screenshot.

## Behaviour

User-visible: nothing intentional. Layer 2 already shipped the new design; this only shuffles source.

Deliberate observable changes:
- Theme-flicker on first paint goes away (script gone)
- `GET /app/...` URLs hard-404 (no users to migrate)
- Page weight drops (no Roboto Mono Google Font fetch, no theme switcher script, ~5,800 fewer lines of CSS shipped)
- `/test/page` returns 404

## Technical approach (phased)

### MCO-142a — Hoist common patterns

P0 audit (no commit):
- Inventory every live `createPage` caller and its destination (`worldPage` → DELETE dead chain, `handleGetSignOut` → port to `pageShell` in 142b, `ProjectPage.kt` → DELETE dead, `TestPage.kt` → DELETE dead in 142c)
- Inventory every CSS file in `static/styles/` against real-token grep
- Baseline grep counts for all Part-C greps

P0a (commit): Delete dead `worldPage()` chain
- Delete `pipeline/world/GetWorldPipeline.kt`
- Delete `templated/world/WorldPage.kt`
- Delete `templated/project/ProjectPage.kt`
- Remove orphan import at `WorldHandler.kt:47`
- Compile + tests pass

P1 (one commit per hoist line item — one of B1, B2, B3, B4, B5, B6, B7, B8, B11, B12 per commit):
- Add new component CSS file(s) and/or DSL helper(s)
- Wire into `pageShell()` for everywhere components (alert, badge, form-field) or leave page-scoped via `stylesheets` parameter (danger-zone, person-row)
- Both old + new coexist after this commit
- B2 alert hoist: switch `SettingsPage.kt:51` `alertContainer()` to new DSL in the same commit (legacy `Alert.kt` is being deleted in 142b)

P2a-d (one commit per page): Migrate consumers
- P2a: Settings — `GeneralTab.kt`, `MembersTab.kt`, `InvitationsTab.kt`, `DangerSection.kt` switch to DSL helpers; `pages/settings-page.css` shrinks to settings-specific rules
- P2b: Profile — `ProfilePage.kt`, `pages/profile-page.css` reduces to ~0 lines
- P2c: Admin — `AdminPage.kt`, `pages/admin-page.css` keeps only admin-search and pagination
- P2d: Landing — `LandingPage.kt`, `pages/landing-page.css` reduces to brand-bar + Microsoft button + landing-feature
- Update test class-name assertions in same commit as the rename
- Screenshot diff at 360px and 1280px after each commit

### MCO-142b — Delete legacy template system (depends on 142a)

P3a: Port `confirmDeleteModal` to `templated/dsl/Modal.kt`
- Preserve `CONFIRM_DELETE_MODAL_ID` and all element IDs (`hxDeleteWithConfirm` + `confirmation-modal.js` contract)
- Replace inline `style = "display: none;"` with `is-hidden` utility class
- Update `pageShell()` to call new DSL modal

P3b: Port `handleGetSignOut` from `createPage` to `pageShell()`

P3c: Delete `templated/common/component/`
- Compile-fail-driven cleanup of dependents

P3d: Delete legacy templates — leaves first, recompile, repeat
- `topbar/TopBar.kt`, legacy `layout/alert/Alert.kt`, `BreadcrumbComponent.kt`, legacy `avatar/avatar.kt`, `chip/Chip.kt`, legacy `tabs/Tabs.kt`, legacy `modal/Modal*.kt` and `templated/common/modal/ConfirmDeleteModal.kt`, `button/GenericButton.kt`, legacy `button/Buttons.kt`, `emptystate/EmptyState.kt`, `searchableselect/`, `searchField/`, `radiogroup/`, `Progress.kt`, `Icons.kt`
- `link/Link.kt` rendering helpers + `LinkComponent` class deleted; sealed interface `Link` moved to `templated/dsl/Link.kt`

P3e: Delete legacy page shells
- `templated/common/page/Page.kt`, `PageStyle.kt`, `PageScript.kt`
- `templated/common/` directory empty or deleted

Acceptance for 142b:
- `rg 'class .*: (Leaf|Node)?Component\b' src/main/kotlin/` → 0
- `rg 'createPage\(' src/main/` → 0
- `rg 'addComponent\(' src/main/` → 0
- Manual smoke test: one `hxDeleteWithConfirm` flow (e.g. world deletion) — modal renders, confirm button enables, delete fires
- Integration test: ported `confirmDeleteModal` renders with all expected element IDs in DOM (validates JS contract since DSL helpers have no unit tests)
- `mvn clean compile && mvn test` green

### MCO-142c — Delete legacy CSS + final sweep (depends on 142b)

P4a: Delete dead-token component CSS files (~22 files)

P4b: Delete `root.css`, `styles.css`, `utilities.css`, `layout.css`

P4c: Delete page CSS for deleted pages (`world-page.css`, `project-page.css`, `idea-page.css`, `create-idea-page.css`, `ideas-page.css`)

P5a: Delete `static/scripts/theme-switcher.js` and `templated/testpage/` and `pages/test-page.css`

P5b: Delete `/test/page` route and `route("/app") { ... }` block from `mainRouter.kt`

P5c: Final greps — all Part-C greps return zero

P5d: Manual visual QA — screenshot every Layer 2 page at 360px and 1280px; diff against pre-cleanup screenshots from each Layer 2 issue

Acceptance for 142c:
- All Part-C greps → 0
- `rg 'fonts.googleapis|fonts.gstatic|Roboto.Mono' src/main/` → 0
- `rg 'data-theme|theme-switcher|mc-org-theme|getThemeScript' src/main/` → 0
- `rg 'SEARCHABLE_SELECT|searchable-select.js' src/main/` → 0
- `route("/app")` block deleted from `mainRouter.kt`
- `/test/page` route deleted
- `theme-switcher.js` deleted
- Production payload smaller (note figure on issue)
- `mvn clean compile && mvn test` green
- Screenshots match Layer 2 baselines

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Hidden consumer of legacy Component breaks compile | MEDIUM | Compile-fail-driven deletion: delete one, compile, fix or revert |
| `confirmDeleteModal` deletion order — used by `pageShell()` | MEDIUM | 142b P3a ports BEFORE 142b P3d deletes; sub-task ordering enforced |
| `hxDeleteWithConfirm` modal ID contract — 15 callers; rename breaks production silently | MEDIUM | 142b P3a preserves all IDs; integration test validates DOM after port; manual smoke required |
| `Link.kt` sealed interface accidentally deleted with rendering helpers | LOW | Only the rendering helpers (`linkComponent`, `actionLink`, `subtleLink`, `dangerLink`, `LinkComponent` class) delete; sealed interface moves to `templated/dsl/Link.kt` |
| Visual regression from CSS specificity changes when hoisting | MEDIUM | One page per commit with screenshot diff at 360px and 1280px |
| Test selectors break on class renames | MEDIUM | Update tests in same commit as hoist (user decision) |
| Premature DSL abstraction | LOW | ≥2 consumer rule; drop helper if turns out to have <1 consumer during impl |
| CSS load-order causes payload bloat | LOW | Extend hardcoded `pageShell()` list only for everywhere components; page-scoped CSS via `stylesheets` parameter |
