---
name: docs-frontend
description: Frontend implementation reference for MC-ORG's design system — Kotlin HTML DSL component functions, CSS component classes, design tokens, layout utilities, the page shell, and type-safe links. Use when writing or editing templates, picking a button/badge/modal/card/section component, applying a CSS class or token, or building a new page.
user-invocable: false
---

# Frontend Implementation Reference

How to build UI in MC-ORG's "technical field notebook" design system. Server-side rendered Kotlin HTML
DSL + HTMX. Light "paper" theme — the "Daylight Field Notebook" Overworld palette (warm paper + sepia ink).

**This skill = how to write it** (DSL function names, CSS class names, tokens that actually exist in code).
For the *why* — visual spec, motion, mobile rules, design rationale — load `docs-product`.
For the full `hx*` helper catalogue and swap mechanics, load `docs-htmx`.

DSL lives in `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/dsl/`.
CSS lives in `webapp/mc-web/src/main/resources/static/styles/`.

---

## Page shell

`pageShell()` replaces the old `createPage()`. It emits the full HTML document, loads `reset.css` +
`design-tokens.css` + a core set of component stylesheets, loads HTMX from CDN, and auto-injects the
shared confirm-delete modal and the toast/alert container.

```kotlin
pageShell(
    pageTitle = "Projects",
    user = user,                                   // TokenProfile? — gates Profile link in header
    stylesheets = listOf("/static/styles/pages/project-list.css"),  // extra page/component CSS
    scripts = listOf("/static/scripts/foo.js"),    // extra deferred scripts
) {
    appHeader(worldName = "Survival", worldId = 1, user = user, isWorldAdmin = true) {
        breadcrumb("Worlds", "/worlds") / current("Survival")
    }
    main {
        container {
            // page body
        }
    }
}
```

Always loaded by the shell (do NOT re-list in `stylesheets`): `app-header`, `btn`, `modal`, `alert`,
`badge`, `page-heading`, `section`, `tabs`, `data-table`. Any other component CSS the page uses must be
passed via `stylesheets`.

GET handlers return a full `pageShell { }`. Mutating handlers (POST/PUT/PATCH/DELETE) return HTML
**fragments**, never a full page.

---

## Design tokens

Defined in `design-tokens.css`. Never hardcode hex, px, or font names — always `var(--token)`.

**Color:** `--bg-base` `--bg-surface` `--bg-raised` `--border` `--border-strong` · `--text-primary`
`--text-muted` `--text-disabled` · `--accent` (lapis — act/links) `--accent-hover` `--accent-muted` (info
wash + badges + focus ring) `--on-accent` (cream text on an accent fill) · `--green` (done) `--amber` (warn)
`--red` (danger) · `--progress` `--green-bg` `--red-bg`. **One hue, one role** — see `docs-product` for the
full palette map and the load-bearing colour-blind constraint (never signal state by colour alone).

**Fonts:** `--font-ui` (IBM Plex Mono — chrome, headings, badges, labels, buttons) ·
`--font-body` (Inter — body text, table cells, prose)

**Type scale:** `--text-xs` 11px · `--text-sm` 13px · `--text-base` 15px · `--text-ui` 13px · `--text-label` 11px

**Spacing (4px grid):** `--space-1` 4 · `--space-2` 8 · `--space-3` 12 · `--space-4` 16 · `--space-5` 24 ·
`--space-6` 32 · `--space-8` 48

**Layout:** `--max-width` 1080px · `--breakpoint-mobile` 768px · `--radius` 6px

---

## Layout & utility classes

From `design-tokens.css`. Prefer the DSL wrappers; drop to raw classes for ad-hoc layout.

| DSL | HTML | Notes |
|-----|------|-------|
| `container { }` | `.container` | max-width 1080px, centered, horizontal padding |
| `surface { }` | `.surface` | bg-surface + border + radius (card panel) |
| `divider()` | `.divider` | 1px top border in `--border` |
| `pageHeading(title, subtitle?)` | `.page-heading` | `h1` title + optional subtitle |

Utility classes (use directly): `.flex` `.flex-col` `.items-center` `.justify-between` ·
`.gap-1`–`.gap-5` · `.w-full` · `.mt-4 .mt-5 .mb-4 .mb-5 .p-4 .p-5` · `.section-label` (uppercase tracked
muted label) · `.subtle` (muted small text) · `.is-hidden` (display:none) · `.stack` / `.stack--xs`
(vertical flex gap) · `.cluster` / `.cluster--xs` (horizontal flex gap).

---

## Buttons (`Buttons.kt`)

Canonical variants — `FlowContent` extensions taking a content lambda. Emit `btn btn--<variant>` (+ `btn--sm`
when `small = true`). These are the ones to use.

```kotlin
primaryButton { +"Create" }                 // .btn--primary  — main CTA (accent)
secondaryButton { +"Add task" }             // .btn--secondary — supporting (raised bg)
ghostButton { +"Cancel" }                   // .btn--ghost     — tertiary
dangerButton { +"Delete" }                  // .btn--danger    — destructive
primaryButton(small = true) { +"Go" }       // adds .btn--sm
```

To wire HTMX or set attributes, put helpers inside the lambda:

```kotlin
primaryButton {
    hxPost(Link.Worlds.world(worldId).projects().to)
    hxTarget("#projects-view")
    +"Create project"
}
```

**Icon buttons** (`iconButton`) — render an SVG icon, `btn--icon-only` + a color/size variant:

```kotlin
iconButton(Icons.DELETE, ariaLabel = "Delete", color = IconButtonColor.DANGER, size = ButtonSize.SMALL) {
    hxDelete(...); hxTarget(...)
}
```

For a **navigation link styled as a button**, use a plain anchor with the button classes:
`a(classes = "btn btn--primary") { href = Link.Ideas.single(id); +"View" }`.

These five — `primaryButton`, `secondaryButton`, `ghostButton`, `dangerButton`, `iconButton` — are the entire
button API. There is no `actionButton`/`neutralButton`/`backButton` family.

---

## Badges (`Badge.kt`)

```kotlin
statusBadge(BadgeStatus.IN_PROGRESS)        // NOT_STARTED | IN_PROGRESS | DONE | BLOCKED
badge("v1.21", BadgeVariant.NEUTRAL)        // NEUTRAL | ACCENT | DANGER
```

`ProjectStage.toBadgeStatus()` / `.toDisplayStatus()` map domain stages to badge state.
Badges are text-only — no icons inside.

## Progress bar (`ProgressBar.kt`)

```kotlin
progressBar(current = 32, total = 64)              // 4px, .progress
progressBar(current, total, large = true)          // 6px, .progress--lg (project cards)
```

Fill turns green (`.progress__fill--complete`) at 100%. Width is a computed inline `style` — this is the one
sanctioned inline-style exception (a dynamic percentage can't be a class).

## Plan/Execute toggle (`Toggle.kt`)

```kotlin
planExecuteToggle(worldId, active = "plan")   // active: "plan" | "execute"
```

Fixed-width pill, two segments, each `hx-get`s the `list-fragment` endpoint and pushes the URL. `.toggle` /
`.toggle__btn` / `.toggle__btn--active`.

## Section (`Section.kt`)

```kotlin
section(title = "Resources", eyebrow = "TO GATHER", subtitle = null, tight = false, card = false) {
    // DIV body
}
```

`eyebrow` renders as a `.section-label`. `card = true` wraps the body in `.section__card`; `tight = true`
reduces vertical rhythm.

## Empty states (`EmptyState.kt`)

```kotlin
emptyState("No resources defined", body = "Add resources to get started.") {
    primaryButton { +"Switch to Execute" }   // optional actions block
}

emptyStateCards(id = "world-empty") {         // two equal-weight cards
    // each card built with surface { } etc.
}
```

## Modals (`Modal.kt`)

```kotlin
modal(id = "delete-confirm", title = "Delete project") {
    p { +"This cannot be undone." }
    modalActions { ghostButton { +"Cancel" }; dangerButton { +"Delete" } }
}

// Modal with a baked-in HTMX form (resets + closes on success automatically):
modalForm(id = "create-project", title = "New project",
          action = Link.Worlds.world(worldId).projects().to,
          hxTarget = "#project-card-list", hxSwap = "afterbegin") {
    input(classes = "form-control") { name = "name" }
    modalActions { primaryButton { +"Create" } }
}
```

Open/close via the `<dialog>` element (`showModal()` / `close()`). For destructive actions prefer
`hxDeleteWithConfirm(...)` (see docs-htmx) — it drives the shared confirm-delete modal that `pageShell`
already injects.

## Tabs (`Tabs.kt`)

```kotlin
tabStrip(
    tabs = listOf(TabItem("general", "General", "/worlds/$id/settings?tab=general"), ...),
    activeValue = "general",
    hxTarget = "#settings-panel",
    variant = TabVariant.DEFAULT,   // or PILLS
)
```

## Task list (`TaskList.kt`)

```kotlin
taskList(worldId, projectId, tasks) { }     // <ul id="task-list"> of taskRowItem
addTaskInline(worldId, projectId)           // inline "+ Add task" reveal-form
```

`taskRowFragment(worldId, projectId, task)` returns a single `<li>` string for HTMX afterbegin swaps.
Checkboxes `hx-patch` the complete endpoint; rows get `.task-row--done`.

## Resource row (`ResourceRow.kt`)

```kotlin
resourceRow(id, worldId, projectId, itemName, current, required, source = "Iron Farm")
```

Execute-view counter row: name, progress bar, `current / required` count, and ±counter buttons
(`-1728 -64 -1 +1 +64 +1728`) that `hx-patch` the gathering endpoint. `.resource-row--complete` when done.

## Cards

`worldCard(world)` / `worldCardList(worlds)` · `projectCard(worldId, project)` /
`projectCardList(worldId, projects)` (execute view) · `planProjectCard(...)` / `planProjectCardList(...)`
(plan view). Take domain list-item models and render the full card.

## Toasts / alerts (`Alert.kt`)

`pageShell` injects `alertContainer()` (`<ul id="alert-container">`). To push a toast from a fragment
response, render an `<li>` via `createAlert`:

```kotlin
createAlert(id = "toast-1", type = AlertType.SUCCESS, title = "Saved", message = null)
// types: SUCCESS | ERROR | WARNING | INFO. SUCCESS/INFO auto-close after 3s.
```

## Icons (`Icons.kt`)

```kotlin
iconComponent(Icons.SEARCH, size = IconSize.MEDIUM)   // inline SVG <div class="icon ...">
Icons.DELETE, Icons.BACK, Icons.CHECK, Icons.CLOSE, Icons.MENU, Icons.Users.ADD, ...
```

SVGs live under `/static/icons/`. Note: `IconColor` variants all currently resolve to `inherit` (semantic
per-variant colors were stripped in the MCO-161 token sweep and are a pending follow-up) — icons take the
surrounding text color.

---

## Forms (`form.css`, `RadioGroup.kt`, `SearchableSelect.kt`, `ResourceSearch.kt`)

`.form-control` is the base class for `input` / `select` / `textarea`:

```kotlin
input(classes = "form-control") { name = "name"; placeholder = "Project name" }
textarea(classes = "form-control") { name = "description" }
select(classes = "form-control") { ... }
```

Supporting classes: `.is-invalid` (error border) · `.form-error` / `.validation-error-message` (red message,
default HTMX error target is `.form-error`) · `.form-help-text` · `.required-indicator` · `.form-suffix`
(unit label) · `.filter-radio-label` / `.filter-checkbox-label` (styled radio/checkbox option rows).

DSL helpers: `radioGroup(...)`, `searchableSelect<V>(...)`, `resourceSearch()` for the item picker.

---

## Data table & callout — CSS classes, no DSL helper

`.data-table` (`data-table.css`) and `.callout` (`callout.css`) are styled CSS classes with no DSL wrapper —
render the markup directly. Build a data table on a raw `table`:

```kotlin
table(classes = "data-table") {
    thead { tr { th { +"Item" }; th { +"Qty" }; th { +"Source" } } }
    tbody { /* rows; on mobile use data-label attrs per docs-product */ }
}
```

For a callout, emit the `.callout` structure directly (left-border notice). Variants: default = warning
(amber + ⚠), `.callout--error` (redstone + ✕), `.callout--success` (grass + ✓), `.callout--info` (the
"quiet lapis" — lapis rule + ⓘ, our info treatment). If a reusable DSL helper is worth adding, build it
against these classes.

---

## Type-safe links (`Link.kt`)

Build URLs through the `Link` sealed interface, not string literals. `.to` yields the path.

```kotlin
Link.Worlds.to                                   // /worlds
Link.Worlds.world(worldId).to                    // /worlds/{id}
Link.Worlds.world(worldId).projects().to         // /worlds/{id}/projects
Link.Worlds.world(worldId).project(pid).to       // /worlds/{id}/projects/{pid}
Link.Worlds.world(worldId).project(pid).tasks().task(tid)   // .../tasks/{tid}
Link.Worlds.world(worldId).settings().to         // /worlds/{id}/settings
Link.Ideas.to / Link.Ideas.single(id)
Link.Profile.to · Link.AdminDashboard.to · Link.Home.to
```

URLs follow the IA scheme (`/worlds/:worldId/...`) — there is no `/app/` prefix anymore.

---

## HTMX in new pages

Helpers (`hxGet/hxPost/hxPut/hxPatch/hxDelete`, `hxTarget`, `hxSwap`, `hxTrigger`, `hxPushUrl`, `hxInclude`,
`hxOutOfBands`, `hxDeleteWithConfirm`, `hxTargetError`) and swap mechanics are catalogued in **docs-htmx** —
load it for fragment/OOB/inline-edit patterns. Two new-system specifics:

- Use `Link.*.to` for HTMX URLs (new `/worlds/...` routes), and the new button classes shown above. (The
  example snippets inside docs-htmx still show legacy `/app/...` routes, `btn--action`, and `notice--*`
  classes — translate them to the new system.)
- Response element `id` must match the `hxTarget` selector; mutating handlers return fragments only.

---

## Hard rules

- No inline `style =` — use CSS classes. (Sole exception: a computed dimension like progress-bar width.)
- No hardcoded colors / px / font names — always `var(--token)`.
- Only IBM Plex Mono (`--font-ui`) and Inter (`--font-body`).
- Buttons use `--radius` (6px), never pill.
- Status badges are text-only; warning callouts are left-border, never full-width banners.
- Import `kotlinx.html.stream.createHTML` — never `kotlinx.html.createHTML`.
- All responses are HTML fragments — never JSON.
