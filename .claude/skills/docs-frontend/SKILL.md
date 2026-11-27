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

Everything named here exists in the code. *(Until 2026-11-27, MCO-617, this file also documented
`planExecuteToggle`, `worldCard`, `planProjectCard`, `projectCard` and settings tabs, none of which
existed any more.)*

DSL lives in `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/dsl/`.
CSS lives in `webapp/mc-web/src/main/resources/static/styles/`.

---

## Page shell

`pageShell()` replaces the old `createPage()`. It emits the full HTML document, links the one
stylesheet bundle, loads HTMX from CDN, and auto-injects the shared confirm-delete modal and the
toast/alert container.

```kotlin
pageShell(
    pageTitle = "Seam — Survival",
    user = user,                                   // TokenProfile? — gates Profile link in header
) {
    appHeader(worldName = "Survival", worldId = 1, user = user) {
        link("Worlds", "/worlds").current("Survival")
    }
    main {
        container {
            worldBar(worldId = 1, active = WorldTab.ROADMAP, isWorldAdmin = true) {
                newProjectAffordance(worldId = 1)          // "+ New project", the bar's one action
            }
            // page body
        }
    }
}
```

`worldBar` (`Navigation.kt`) draws the world tabs, Roadmap and Settings (admins only), with an
optional action slot on the right. The phone header is part of `appHeader`; the project page adds
its own second phone header (`.project-detail__mobile-header`), which the shell round (MCO-607)
replaces with a drawer.

**There is no per-page stylesheet or script list** (MCO-514, MCO-547). Every sheet under
`static/styles/` is in one bundle, `StylesheetBundle.kt`, served at `/static/seam.<hash>.css`,
and every script under `static/scripts/` is in `ScriptBundle.kt` at `/static/seam.<hash>.js`,
deferred — so a component's CSS and JS travel with its markup to every page, including htmx
fragments from shared endpoints. Adding a file means adding it *and* its name to the bundle
object; `StylesheetBundleTest` / `ScriptBundleTest` fail until the two agree. Rules, all
test-enforced:

- **Never link a stylesheet or script by hand** — no `"/static/styles/..."` or
  `"/static/scripts/..."` in a template.
- **A page sheet must not re-declare a component's bare class** (`.badge { }` in `idea-hub.css`).
  It used to override only on that page; in one bundle it overrides everywhere. Scope it to the
  page's own markup (`.idea-card__badges .badge { }`) or change the component.
- **A script runs on every page**, so it delegates from the document or looks its element up and
  returns when it is missing. It exposes behaviour only by assigning to `window.<name>`, and each
  such name is defined in exactly one file. Item-search results call `selectSearchedItem`, which
  is `item-search.js`'s; a new combo renders the `.item-search-combo` class hooks, not a handler.

GET handlers return a full `pageShell { }`. Mutating handlers (POST/PUT/PATCH/DELETE) return HTML
**fragments**, never a full page.

---

## Design tokens

Defined in `design-tokens.css`. Never hardcode hex, px, or font names — always `var(--token)`.

**Color:** `--bg-base` `--bg-surface` `--bg-raised` `--border` `--border-strong` · `--text-primary`
`--text-muted` `--text-disabled` · `--accent` (lapis — act/links) `--accent-hover` `--accent-muted` (info
wash + badges + focus ring) `--on-accent` (cream text on an accent fill) · `--green` (done) `--amber` (warn)
`--red` (danger) `--red-hover` · `--progress` `--green-bg` `--red-bg`. **One hue, one role** — see
`docs-product` for the full palette map and the load-bearing colour-blind constraint (never signal state by
colour alone).

**Fonts:** `--font-ui` (IBM Plex Mono — chrome, headings, badges, labels, buttons) ·
`--font-body` (Inter — body text, table cells, prose)

**Type scale:** `--text-xs` 11px · `--text-sm` 13px · `--text-base` 15px · `--text-ui` 13px ·
`--text-label` 11px · `--text-lg` 18px · `--text-xl` 20px (in-page view titles). There is **no**
`--text-heading` — it was a documented-but-undefined phantom, folded into `--text-xl` by MCO-388.

**Spacing (4px grid):** `--space-1` 4 · `--space-2` 8 · `--space-3` 12 · `--space-4` 16 · `--space-5` 24 ·
`--space-6` 32 · `--space-8` 48 · `--space-10` 80

**Layout:** `--max-width` 1080px · `--breakpoint-mobile` 768px · `--radius` 6px · `--radius-pill` 999px
(badges/chips only, never buttons) · `--transition-base` 150ms ease-in-out

**Elevation:** `--shadow-card` (resting card) · `--shadow-pop` (dropdown/popover). Ink-coloured, two
levels only — don't write a raw `box-shadow` for either role.

---

## Layout & utility classes

From `design-tokens.css`. Prefer the DSL wrappers; drop to raw classes for ad-hoc layout.

| DSL | HTML | Notes |
|-----|------|-------|
| `container { }` | `.container` | max-width 1080px, centered, horizontal padding |
| `surface { }` | `.surface` | bg-surface + border + radius (card panel) |
| `divider()` | `.divider` | 1px top border in `--border` |
| `pageHeading(title, subtitle?)` | `.page-heading` | `h1` title + optional subtitle |

Utility classes (use directly): `.section-label` (uppercase tracked muted label) · `.subtle` (muted
small text) · `.is-hidden` (display:none) · `.stack` / `.stack--xs` (vertical flex gap) · `.cluster` /
`.cluster--xs` (horizontal flex gap) · `.mt-4`.

That is the whole list. The Tailwind-style set (`.flex`, `.gap-*`, `.w-full`, `.p-4`, …) was documented
here but had zero users anywhere in the codebase and was deleted in MCO-394 — layout goes in the
component's own class. Don't re-add it.

---

## Buttons (`Buttons.kt`)

**As built, the four text-button helpers below have no callers.** Every button in the app is a raw
`button(classes = "btn btn--<variant>")` or an anchor with those classes. The component sweep
(MCO-606, decision Q10) ports every surface through the helpers before the first design is ported;
until it lands, match the raw classes, and prefer the helper in new code.

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
    hxPost(Link.Worlds.world(worldId).project(projectId).tasks().to)
    hxTarget("#task-list")
    +"Add task"
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
button API. There is no `actionButton`/`neutralButton`/`backButton` family. `GenericButton.kt` is older
and is not one of them.

---

## Badges (`Badge.kt`)

```kotlin
projectStateBadge(projectId, project.state)   // ProjectStateBadge.kt — a project's state, everywhere
badge("v1.21", BadgeVariant.NEUTRAL)          // NEUTRAL | ACCENT | DANGER
statusBadge(BadgeStatus.IN_PROGRESS)          // NOT_STARTED | IN_PROGRESS | DONE | BLOCKED
```

`projectStateBadge` is the one to use for a project: it labels the seven `ProjectState` values and
maps them onto four badge styles (docs-product, "Status Badges"). `statusBadge` survives at the
roadmap's start card only. A badge says its state in words; a glyph beside the words only in the
roadmap's dense cells.

## Progress bar (`ProgressBar.kt`)

```kotlin
progressBar(current = 32, total = 64)              // 4px, .progress
progressBar(current, total, large = true)          // 6px, .progress--lg (project cards)
```

Fill turns green (`.progress__fill--complete`) at 100%. Width is a computed inline `style` — this is the one
sanctioned inline-style exception (a dynamic percentage can't be a class).

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

`modalForm` has callers; `modal()` has none, and most dialogs are hand-built `dialog` elements.
Which shape every dialog takes is the component sweep's modal decision (MCO-606), which the
delete tiers (MCO-615) build on.

```kotlin
modal(id = "delete-confirm", title = "Delete project") {
    p { +"This cannot be undone." }
    modalActions { ghostButton { +"Cancel" }; dangerButton { +"Delete" } }
}

// Modal with a baked-in HTMX form (resets + closes on success automatically):
modalForm(id = "add-task", title = "New task",
          action = Link.Worlds.world(worldId).project(projectId).tasks().to,
          hxTarget = "#task-list", hxSwap = "afterbegin") {
    input(classes = "form-control") { name = "name" }
    modalActions { primaryButton { +"Create" } }
}
```

Open/close via the `<dialog>` element (`showModal()` / `close()`). For destructive actions prefer
`hxDeleteWithConfirm(...)` (see docs-htmx) — it drives the shared confirm-delete modal that `pageShell`
already injects.

## Tabs (`Tabs.kt`)

`tabStrip(tabs = listOf(TabItem(...)), activeValue, hxTarget, variant = TabVariant.DEFAULT | PILLS)`.
Its only caller is the invitations filter on world settings (`settings/InvitationsTab.kt`), as
pills. Settings pages are one stacked page with the danger zone last, not tabs (docs-ia, "Settings
Pages"). The world's own sections are `worldTabs`, not this.

## Task list (`TaskList.kt`)

```kotlin
taskList(worldId, projectId, tasks) { }     // <ul id="task-list"> of taskRowItem
addTaskInline(worldId, projectId)           // inline "+ Add task" reveal-form
```

`taskRowFragment(worldId, projectId, task)` returns a single `<li>` string for HTMX afterbegin swaps.
Checkboxes `hx-patch` the complete endpoint; rows get `.task-row--done`.

## Chest counts (`pages/ChestCounts.kt`)

```kotlin
driftChip(worldId, projectId, rowId, resourceId, itemName, current, measured)  // chests ≠ typed count
followedLabel(measured, followed)    // "from 4 chests · 2m ago" on a storage-tracked project
```

`driftChip` renders only on disagreement and offers "Set to N", which posts `/adopt` and replaces
`#rowId`. Styles are `.resource-row__drift*`, `.resource-row__adopt` and `.followed-count` in
`components/resource-row.css`.

## Page components

- **Work row** (`pages/WorkRow.kt`): `workRowCollapsed` / `workRowStrip` draw one line of the
  project's work list and its "Log" strip; `smallJobChip` the short tail. Each has an `…Html`
  twin that returns the fragment string for an htmx swap.
- **Items:** `itemGlyph(itemId, size)` (`ItemGlyphIcon.kt`) draws an item's glyph, tinted by CSS.
  `formatItemCount(n)` writes "578 sb + 3 stacks + 32", and `formatPlainCount(n)` writes "101,121"
  (`ItemQuantity.kt`). One item component for glyph, name, kind and quantity is MCO-519 / MCO-608.
- **World empty state** (`pages/NewProjectDoors.kt`): `worldEmptyState(worldId)`, the two equal
  cards; `newProjectMenu(worldId)` (`NewProjectMenu.kt`) is the "+ New project" menu whose three
  doors the first card repeats.
- **World tally** (`WorldTally.kt`): `worldTally(tally, compact)`, the Worlds page's
  "N in flight · N done" line.
- **Danger zone** (`DangerZone.kt`): `dangerZone(title, description) { }`, last on a settings page.
- **Person row** (`PersonRow.kt`): `personRow(...)` for members and invitations.

The draft list (`idea/DraftListPage.kt`) still borrows `.project-card` from the project card that
was removed; nothing else uses it.

## Toasts / alerts (`Alert.kt`)

`pageShell` injects `alertContainer()` (`<ul id="alert-container">`). To push a toast from a fragment
response, render an `<li>` via `createAlert`:

```kotlin
createAlert(id = "toast-1", type = AlertType.SUCCESS, title = "Saved", message = null)
// types: SUCCESS | ERROR | WARNING | INFO. SUCCESS/INFO auto-close after 3s.
```

## Icons (`Lucide.kt`, `Icons.kt`)

Two helpers, which is one more than the rule in docs-product ("Iconography") allows. Which survives
is an open decision.

```kotlin
lucide("search", size = 16)                          // inline Lucide <svg class="icon">, sized per call
iconComponent(Icons.SEARCH, size = IconSize.MEDIUM)   // the app's own SVGs, in a <div class="icon ...">
```

`iconComponent`'s SVGs live under `/static/icons/`. Its `IconColor` variants all resolve to
`inherit`, and its `.icon--sm/md/lg` size classes have no CSS rule, so its icons take the
surrounding text colour and their size from the SVG.

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

DSL helpers: `radioGroup(...)`, `searchableSelect<V>(...)`.

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
Link.Worlds.world(worldId).to                    // /worlds/{id} (redirects to the roadmap)
Link.Worlds.world(worldId).roadmap().to          // /worlds/{id}/roadmap — where a world opens
Link.Worlds.world(worldId).project(pid).to       // /worlds/{id}/projects/{pid}
Link.Worlds.world(worldId).project(pid).settings().to       // .../projects/{pid}/settings
Link.Worlds.world(worldId).project(pid).tasks().task(tid)   // .../tasks/{tid}
Link.Worlds.world(worldId).settings().to         // /worlds/{id}/settings
Link.Ideas.to / Link.Ideas.single(id)
Link.Profile.to · Link.AdminDashboard.to · Link.Home.to
```

URLs follow the IA scheme (`/worlds/:worldId/...`) — there is no `/app/` prefix anymore.

---

## HTMX in new pages

Helpers (`hxGet/hxPost/hxPut/hxPatch/hxDelete`, `hxTarget`, `hxSwap`, `hxTrigger`, `hxPushUrl`, `hxInclude`,
`hxOutOfBands`, `hxDeleteWithConfirm`) and swap mechanics, including what htmx 4 changed, are catalogued
in **docs-htmx** — load it for fragment/OOB/inline-edit patterns. Two new-system specifics:

- Use `Link.*.to` for HTMX URLs (`/worlds/...` routes — no `/app/` prefix), and the button classes
  shown above. docs-htmx examples use the current system.
- Response element `id` must match the `hxTarget` selector; mutating handlers return fragments only.

---

## Hard rules

- No inline `style =` — use CSS classes. (Sole exception: a computed dimension like progress-bar width.)
- No hardcoded colors / px / font names — always `var(--token)`.
- Only IBM Plex Mono (`--font-ui`) and Inter (`--font-body`).
- Buttons use `--radius` (6px), never pill.
- Status badges say their state in words; warning callouts are left-border, never full-width banners.
- Import `kotlinx.html.stream.createHTML` — never `kotlinx.html.createHTML`.
- All responses are HTML fragments — never JSON.
