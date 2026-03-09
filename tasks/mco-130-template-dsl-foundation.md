---
linear-issue: MCO-130
parent-epic: MCO-128
status: approved
phase: 1
depends-on:
  - MCO-129  # Feature 1: Self-hosted fonts + CSS design tokens
created: 2026-03-09
---

# Template DSL Foundation

## Summary

New package of Kotlin HTML DSL extension functions (`presentation/templated/dsl/`)
that emit the docs-product design system's HTML and CSS classes directly — replacing
the Component class hierarchy pattern for new page development. Both old and new
systems coexist during the transition. Initial scope is the components needed for
Feature 5 (navigation chrome + page shell), with stub signatures for components
needed by later features.

## User value

**Personas served:** All three — Casual player, Technical player, Worker — indirectly.
This is infrastructure that enables the page rewrites (Features 5–12) which deliver
the direct user value.

**What changes for the user:** Nothing yet. This feature produces no user-visible
changes. It creates the building blocks that Features 5–12 use to deliver the new
design system.

**What happens if we don't build it:** Features 5–12 cannot be built with the new
design system. Page rewrites would either use the old Component hierarchy
(perpetuating its problems: 12-param constructors, `render(consumer)` indirection,
class explosion) or each page would re-invent its own DSL patterns inconsistently.

## Scope

**Fully implemented (needed by Feature 5):**
- `Buttons.kt`: `primaryButton`, `secondaryButton`, `ghostButton`, `dangerButton`
  as `FlowContent` extension functions with lambda content blocks. `btn--sm` modifier
  via optional `small` parameter. Emit BEM base + modifier (e.g. `class="btn btn--primary"`)
  — verify against Feature 1 CSS to confirm pattern.
- `Navigation.kt`: `appHeader` (56px, desktop breadcrumb + mobile variants),
  `BreadcrumbBuilder` class with `/` operator for segment chaining, scoped inside
  `appHeader` lambda to prevent misuse. `current()` as terminal operation.
- `Layout.kt`: `pageShell` (replaces `createPage` for new pages — full HTML document
  with self-hosted fonts, new CSS tokens only, HTMX + response-targets scripts,
  `hx-ext="response-targets"` on `<body>`, `<meta charset="utf-8">`, no Google Fonts,
  no old CSS, no theme script, no confirmation modal script),
  `container`, `surface`, `divider`.
- `Toggle.kt`: `planExecuteToggle` (CSS-only, emits `.toggle` / `.toggle__btn` /
  `.toggle__btn--active` markup). Accepts `String` parameter (`"PLAN"` or `"EXECUTE"`)
  for active state — avoids compile dependency on Feature 3's `ViewPreference` enum.
  Refactor to use `ViewPreference` when MCO-131 lands.

**Minimal implementations (needed soon by Features 6–12):**
- `Badge.kt`: `statusBadge(status: BadgeStatus)` — `BadgeStatus` enum (NOT_STARTED,
  IN_PROGRESS, DONE, BLOCKED) in dsl package, mapping to `.badge--*` CSS classes.
  Calling templates map from domain types (e.g. `ProjectStage`) to `BadgeStatus`.
- `ProgressBar.kt`: `progressBar(current: Int, total: Int, large: Boolean = false)`
  — emits `.progress`, `.progress__fill`, handles complete state.
- `Modal.kt`: `modal(id, title)` — basic structure using `<dialog>` element with
  `.modal-backdrop`, `.modal`, `.modal__heading`, `.modal__body`, `.modal__actions`.

**Stub files (fleshed out when consuming features are built):**
- `ResourceRow.kt`, `DataTable.kt`, `EmptyState.kt`, `Callout.kt`, `ProjectCard.kt`,
  `TaskList.kt` — empty function bodies (no-ops), NOT `TODO()` calls. Compile
  without rendering meaningful output.

**Excluded:**
- Deletion of old Component files — Feature 13 (MCO-142).
- Wiring toggle to DB persistence — Feature 3 (MCO-131).
- HTMX attributes on components — added per-feature when pages are built.
- Old button extension functions (`actionButton`, `neutralButton`, etc.) — untouched.
- CSS implementation — Feature 1 provides all CSS classes. This feature only emits
  HTML that references them.
- ResourceRow counter logic, DataTable sorting, mobile stacked cards — Features 9/10.

## Behaviour

**From the user's perspective:** No visible change. Developer-facing infrastructure.

**From the developer's perspective:**

New page templates import from `app.mcorg.presentation.templated.dsl.*` and use
extension functions directly on `FlowContent`:

```kotlin
import app.mcorg.presentation.templated.dsl.*

fun newProjectListPage(user: TokenProfile, worldName: String): String {
    return pageShell(pageTitle = "Projects — $worldName", user = user) {
        container {
            primaryButton { +"Create Project" }
        }
    }
}
```

**Buttons:**
```kotlin
div {
    primaryButton { +"Save" }
    secondaryButton { +"Add Resource" }
    ghostButton { +"Cancel" }
    dangerButton { +"Delete" }
    primaryButton(small = true) { +"Import" }
}
```
Each emits a `<button class="btn btn--primary">` (base + modifier) with content from
the lambda block. No Component instantiation, no `render()`.

**Navigation:**
```kotlin
appHeader(worldName = "Survival", user = user) {
    breadcrumb("Worlds", "/worlds") / link("Survival", "/worlds/1/projects") / current("Iron Farm")
}
```
Emits `<header class="app-header">` with breadcrumb per docs-product spec. Desktop:
logo + breadcrumb + Ideas link + gear. Mobile: hamburger + world name + gear.
`BreadcrumbBuilder` is scoped inside `appHeader` — `/` operator returns the builder
for chaining, `current()` is the terminal operation. Mobile hamburger is CSS-only
show/hide; drawer JS deferred per epic out-of-scope table.

**pageShell:**
```kotlin
pageShell(pageTitle = "MC-ORG", user = user) {
    appHeader(...) { ... }
    container { /* page content */ }
}
```
Returns complete HTML string. Includes: `<!DOCTYPE html>`, `<meta charset="utf-8">`,
viewport meta, self-hosted font references, new CSS token stylesheet, HTMX script
(same CDN source as existing `PageScript.HTMX`), response-targets script,
`hx-ext="response-targets"` on `<body>`. Does NOT include: Google Fonts links, old CSS
files, old theme script, TopBar, old BreadcrumbComponent, confirmation modal script.

**Toggle:**
```kotlin
planExecuteToggle(active = "EXECUTE")
```
Emits: `<div class="toggle"><button class="toggle__btn">PLAN</button><button
class="toggle__btn toggle__btn--active">EXEC</button></div>`. No HTMX — consuming
page adds that.

**Name collision handling:** Old `dangerButton`/`ghostButton` in `common.button`
package, new ones in `dsl` package. Different signatures (old takes `String` text,
new takes lambda content block) — no compile-time collision even if both imported.
Pages are rewritten wholesale so no file should need both.

**pageShell vs createPage coexistence:** Both exist simultaneously. Old pages use
`createPage()`, new pages use `pageShell()`. They share no code. `pageShell` is a
clean implementation referencing new CSS/fonts only.

## Technical approach

**Module:** `mc-web` only. No new routes, handlers, pipeline steps, or migrations.

**New files:**
```
webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/dsl/
  Buttons.kt        — primaryButton, secondaryButton, ghostButton, dangerButton
  Navigation.kt     — appHeader, BreadcrumbBuilder, breadcrumb DSL
  Layout.kt         — pageShell, container, surface, divider
  Toggle.kt         — planExecuteToggle
  Badge.kt          — BadgeStatus enum, statusBadge (minimal)
  ProgressBar.kt    — progressBar (minimal)
  Modal.kt          — modal, modalActions (minimal)
  ResourceRow.kt    — stub (empty body)
  DataTable.kt      — stub (empty body)
  EmptyState.kt     — stub (empty body)
  Callout.kt        — stub (empty body)
  ProjectCard.kt    — stub (empty body)
  TaskList.kt       — stub (empty body)

webapp/mc-web/src/test/kotlin/app/mcorg/presentation/templated/dsl/
  ButtonsTest.kt
  NavigationTest.kt
  LayoutTest.kt
  ToggleTest.kt
```

**Receiver types:** `FlowContent` for block-level components (buttons, layout,
navigation, toggle). More type-safe than `Tag` — ensures components appear only
where block content is valid.

**Test pattern:** Render to HTML string via `createHTML().div { ... }` (using
`import kotlinx.html.stream.createHTML`), assert on CSS classes, structure, and
content using string contains / regex.

**Skills:** `/docs-development`, `/docs-architecture`, `/docs-product`

**Dependency:** Feature 1 (MCO-129) must be complete — CSS classes must exist. Kotlin
code can be written and tested independently (tests verify HTML output, not rendering).

## Sub-tasks

1. **Buttons.kt + ButtonsTest.kt** — `primaryButton`, `secondaryButton`, `ghostButton`,
   `dangerButton` with `small` parameter. Verify BEM pattern against Feature 1 CSS.
   Tests verify CSS classes and content.
2. **Toggle.kt + ToggleTest.kt** — `planExecuteToggle(active: String)`. Tests for
   both active states. String parameter avoids MCO-131 dependency.
3. **Navigation.kt + NavigationTest.kt** — `appHeader`, scoped `BreadcrumbBuilder`
   with `/` operator, `current()` terminal. Tests for 1/2/3 segments and
   desktop/mobile markup.
4. **Layout.kt + LayoutTest.kt** — `pageShell` (with HTMX, response-targets,
   charset, no Google Fonts), `container`, `surface`, `divider`. Tests verify
   HTML structure.
5. **Stub files + minimal implementations** — Badge (with `BadgeStatus` enum),
   ProgressBar, Modal (minimal); ResourceRow, DataTable, EmptyState, Callout,
   ProjectCard, TaskList (empty body stubs). No tests for stubs.

## Acceptance criteria

- [ ] All new DSL functions are in package `app.mcorg.presentation.templated.dsl` — no
      modifications to existing files in `common/`.
- [ ] `primaryButton { +"Save" }` inside `FlowContent` emits a `<button>` with
      correct BEM classes (base + modifier).
- [ ] `dangerButton` in `dsl/` does not conflict with `dangerButton` in
      `common/button/` — both compile independently.
- [ ] `planExecuteToggle(active = "EXECUTE")` emits `<div class="toggle">` with
      exactly two child buttons, one having `.toggle__btn--active`.
- [ ] `appHeader(...)` emits `<header class="app-header">` with breadcrumb navigation.
- [ ] `BreadcrumbBuilder` is scoped inside `appHeader` and supports `/` chaining
      with `current()` terminal.
- [ ] `pageShell(...)` returns complete HTML document with: `<meta charset="utf-8">`,
      self-hosted fonts, new CSS, HTMX script, response-targets script,
      `hx-ext="response-targets"` on `<body>` — no Google Fonts, no old theme script,
      no TopBar, no old CSS, no confirmation modal script.
- [ ] `container`, `surface`, `divider` emit elements with matching CSS classes.
- [ ] Stub files exist, compile, and use empty bodies (not `TODO()`).
- [ ] Badge emits correct `.badge--*` class for each `BadgeStatus` value.
- [ ] ProgressBar emits `.progress` with fill and handles complete state.
- [ ] Unit tests pass for Buttons, Toggle, Navigation, and Layout.
- [ ] `mvn clean compile` passes from root with zero errors.
- [ ] `mvn test` passes — all existing + new tests green.
- [ ] No modifications to any existing file in `presentation/templated/common/`.

## Out of scope

| Item | Reason | Destination |
|------|--------|-------------|
| Delete old Component hierarchy | Cleanup after all pages rewritten | MCO-142 |
| HTMX attributes on DSL components | Added per-page during rewrites | Features 5–12 |
| Toggle DB persistence | Needs JWT + DB infrastructure | MCO-131 |
| ViewPreference enum usage in toggle | Refactored in when MCO-131 lands | MCO-131 |
| ResourceRow counter logic | Tied to execute view page | MCO-138 |
| DataTable sorting + mobile cards | Tied to plan view page | MCO-139 |
| Modal variants (form, confirm) | Built when needed by pages | Features 6–12 |
| Mobile hamburger drawer JS | Needs JS beyond CSS | Post-Feature 5 |
| Lucide icon integration | Independent concern | Standalone follow-up |
| Loading old CSS in pageShell | New pages are fully rewritten | Rejected |

## Tech lead review

**Verdict:** Changes recommended (incorporated above)

**Key corrections applied:**
1. Toggle uses `String` parameter instead of `ViewPreference` — avoids MCO-131 compile dependency
2. `pageShell` includes response-targets script and `hx-ext="response-targets"` on `<body>`, plus `<meta charset="utf-8">`
3. Buttons emit BEM base + modifier (`btn btn--primary`) — verify against Feature 1 CSS
4. `BreadcrumbBuilder` scoped inside `appHeader` lambda with `/` operator and `current()` terminal
5. Stub files use empty bodies, not `TODO()` calls
6. `FlowContent` confirmed as correct receiver type (more type-safe than `Tag`)
7. `BadgeStatus` stays in dsl package (presentation concern, not domain)