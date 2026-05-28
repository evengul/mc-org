---
linear-issue: "MCO-134"
status: approved
phase: 1
created: 2026-03-11
depends-on:
  - MCO-129
  - MCO-130
---

# Navigation Chrome + Page Shell

## Summary
Replace the old `createPage()` / `TopBar` / `BreadcrumbComponent` navigation stack with the new `pageShell()` + `appHeader()` pattern from the DSL foundation (MCO-130). This delivers the 56px navigation header specified in docs-product and docs-ia, with desktop breadcrumb nav and a minimal mobile layout. Old `createPage()` remains untouched for unrewritten pages — both systems coexist during migration. This is the gateway feature: all subsequent page rewrites in the frontend rewrite epic depend on this being solid.

## User value
**Personas:** All three (casual player, technical player, worker). Every authenticated user sees the navigation header on every rewritten page.

**What changes:** Users get a consistent, compact 56px header with breadcrumb-based wayfinding on desktop (`[Logo] Worlds > [World] ... Ideas [gear]`) and a clean mobile layout (`[World Name] [gear]`). The current top bar is taller, uses Google Fonts (latency), and has a flat nav-link list that does not show location context. The new header tells users where they are in the hierarchy at all times.

**If we don't build it:** No subsequent page can be rewritten to the new design system. The entire frontend rewrite blocks on this.

## Scope
**Included:**
- Complete `appHeader()` implementation with desktop and mobile variants per docs-product Navigation Header spec
- CSS for `.app-header`, `.app-header__desktop`, `.app-header__mobile`, `.app-header__logo`, `.app-header__actions`, `.app-header__world-name`, `.app-header__link`, `.breadcrumb`, `.breadcrumb__item`, `.breadcrumb__item--current`, `.breadcrumb__sep` — all defined within `app-header.css`, independent of the old `breadcrumb.css`
- `pageShell()` loads `app-header.css` unconditionally as a base stylesheet alongside `reset.css` and `design-tokens.css`
- `BreadcrumbBuilder` DSL in `Navigation.kt` refined to match IA breadcrumb-by-page table
- Context-aware gear icon: links to `/profile` (no context), `/worlds/:worldId/settings` (world context), or hidden entirely (project context)
- Mobile layout: centered world name + gear icon only (no hamburger button)
- Removal of the existing hamburger button added in MCO-130
- Backward compatibility: old `createPage()`, `TopBar`, `BreadcrumbComponent`, and `BreadcrumbBuilder` (in utils/) remain unchanged and functional

**Excluded:**
- Rewriting any existing page to use `pageShell()` — that is per-page work in subsequent features
- Theme system changes (overworld/nether/end) — deferred per epic plan
- Hamburger button and mobile drawer — deferred to a follow-up feature when drawer content is spec'd
- Unauthenticated pages — landing page and login page continue using old `createPage()`
- Notification badge in new header
- Admin link in new header

## Behaviour

### Desktop (>= 768px)

The `appHeader()` function renders a `<header class="app-header">` at 56px height containing:

1. **Logo** — `<span class="app-header__logo">` displaying "MC-ORG" in IBM Plex Mono.
2. **Breadcrumb nav** — `<nav class="breadcrumb">` with segments separated by `›` (`\u203A`) in `--text-disabled`. Prior segments are `<a class="breadcrumb__item">` links. Current segment is a `<span class="breadcrumb__item breadcrumb__item--current">` in `--text-primary`. Font: IBM Plex Mono, `--text-sm`. Separator spans use class `breadcrumb__sep`.
3. **Actions** — right-aligned `<div class="app-header__actions">` containing:
   - "Ideas" link (`/ideas`) — always visible
   - Gear icon link — conditionally rendered based on context (see Gear Icon Behaviour below)

Breadcrumb content varies by page, per docs-ia breadcrumb table:

| Page | Breadcrumb segments |
|------|-------------------|
| World list | *(none — no breadcrumb rendered)* |
| Project list | `Worlds` > `[World Name]` |
| Project detail | `Worlds` > `[World Name]` > `[Project Name]` |
| Production path | `Worlds` > `[World Name]` > `[Project Name]` > `Path` |
| Roadmap | `Worlds` > `[World Name]` > `Roadmap` |
| Idea Hub | `Ideas` |
| Idea detail | `Ideas` > `[Idea Name]` |

The `Worlds` segment links to `/worlds`. World name links to `/worlds/:worldId/projects`. Project name links to `/worlds/:worldId/projects/:projectId`. Current page segment is not a link.

### Mobile (< 768px)

The `appHeader()` function renders a second div `<div class="app-header__mobile">` containing:

1. **World name** — `<span class="app-header__world-name">` showing the current world name (or "MC-ORG" if no world context), centered, IBM Plex Mono
2. **Gear** — `<a class="app-header__link">` with `⚙` (`\u2699`) and `aria-label="Settings"`, right-aligned — conditionally rendered (same rules as desktop)

No hamburger button is rendered. The existing hamburger button from the MCO-130 stub must be removed. Mobile navigation for this phase relies on breadcrumb/back links and in-page contextual links.

Desktop and mobile variants are shown/hidden via CSS media query at 768px. Both are always rendered in the HTML; CSS `display: none` controls visibility.

### Gear Icon Behaviour

The gear icon is context-aware and conditionally rendered. A private helper function `gearHref(worldId, projectId)` computes the destination or signals that the icon should be hidden.

| Context | worldId | projectId | Gear behaviour |
|---------|---------|-----------|----------------|
| No context (world list, ideas) | `null` | `null` | Links to `/profile` |
| World context (project list, roadmap, world settings) | set | `null` | Links to `/worlds/:worldId/settings` |
| Project context (project detail, production path) | set | set | **Not rendered** |

When `projectId` is set, the gear icon is omitted from both desktop and mobile variants. Project settings navigation will be added when the full epic is complete.

### Component API

```kotlin
// In Navigation.kt — updated signature
fun FlowContent.appHeader(
    worldName: String? = null,
    worldId: Int? = null,
    projectId: Int? = null,
    user: TokenProfile? = null,
    breadcrumbBlock: (BreadcrumbBuilder.() -> BreadcrumbBuilder)? = null
)

// Private helper — computes gear destination or null (do not render)
private fun gearHref(worldId: Int?, projectId: Int?): String? = when {
    projectId != null -> null
    worldId != null -> "/worlds/$worldId/settings"
    else -> "/profile"
}
```

**Usage examples:**

```kotlin
// World list page — no breadcrumb, gear links to /profile
pageShell(pageTitle = "Worlds - MC-ORG", user = user) {
    appHeader(user = user)
    container { /* page content */ }
}

// Project list page — breadcrumb, gear links to world settings
pageShell(pageTitle = "${world.name} - MC-ORG", user = user) {
    appHeader(worldName = world.name, worldId = world.id, user = user) {
        link("Worlds", "/worlds")
        current(world.name)
    }
    container { /* page content */ }
}

// Project detail page — full breadcrumb, gear NOT rendered
pageShell(pageTitle = "${project.name} - MC-ORG", user = user) {
    appHeader(
        worldName = world.name,
        worldId = world.id,
        projectId = project.id,
        user = user
    ) {
        link("Worlds", "/worlds")
        link(world.name, "/worlds/${world.id}/projects")
        current(project.name)
    }
    container { /* page content */ }
}

// Idea Hub — no world context, gear links to /profile
pageShell(pageTitle = "Ideas - MC-ORG", user = user) {
    appHeader(user = user) {
        current("Ideas")
    }
    container { /* page content */ }
}
```

### Empty states

- When `breadcrumbBlock` is null, the `<nav class="breadcrumb">` element is not rendered.
- When `worldName` is null, the mobile world name displays "MC-ORG".

### Edge cases

- **Long world/project names:** Truncate with CSS `text-overflow: ellipsis` and `max-width` on breadcrumb segments and mobile world name.
- **Admin users:** No admin link in new header per IA spec. Admin accessible via old nav or direct URL.
- **Deep breadcrumbs (4 segments):** Production path page has 4 segments — fits within 1080px at `--text-sm`. No special handling needed beyond truncation.

## Technical approach

**Module:** `mc-web` only. No database changes. No migrations.

**Files modified:**
- `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/dsl/Navigation.kt` — Refine `appHeader()` in place: add `worldId: Int?` and `projectId: Int?` params, extract `gearHref()` private helper, remove existing hamburger button, conditionally render gear on both desktop and mobile, use `breadcrumb__sep` as separator class.
- `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/dsl/Layout.kt` — `pageShell()` unconditionally loads `app-header.css` as a base stylesheet in `<head>` alongside `reset.css` and `design-tokens.css`.

**Files created:**
- `webapp/mc-web/src/main/resources/static/styles/components/app-header.css` — All CSS for the navigation header. Defines all breadcrumb classes (`breadcrumb__item`, `breadcrumb__item--current`, `breadcrumb__sep`) independently of the old `breadcrumb.css`. The two files do not conflict — different class names, different consumers.

**No files deleted.** Old `createPage()`, `TopBar`, `BreadcrumbComponent`, old `breadcrumb.css`, and `utils/BreadcrumbBuilder` all remain for unrewritten pages.

**Key decisions:**
1. `app-header.css` is a base stylesheet, not per-page. Loaded unconditionally by `pageShell()`.
2. `app-header.css` owns its breadcrumb styles. Old `breadcrumb.css` is untouched.
3. `gearHref()` returns `String?` — `null` means do not render the gear icon.
4. No JavaScript required. Hamburger/drawer JS is deferred.

## Sub-tasks
- [ ] 1. Create `app-header.css` — 56px height, desktop flex layout, mobile layout, breadcrumb classes with IBM Plex Mono `--text-sm`, separators in `--text-disabled`, current segment in `--text-primary`, media query at 768px, text truncation, hover states
- [ ] 2. Update `pageShell()` in `Layout.kt` — unconditionally load `app-header.css` alongside `reset.css` and `design-tokens.css`
- [ ] 3. Refine `appHeader()` in `Navigation.kt` — add `worldId`/`projectId` params, extract `gearHref()` helper, conditionally render gear, remove hamburger button, use `breadcrumb__sep` separator class
- [ ] 4. Verify backward compatibility — `mvn clean compile` + `mvn test` pass, old pages unaffected

## Acceptance criteria
- [ ] `pageShell()` renders a full HTML page with `reset.css`, `design-tokens.css`, and `app-header.css` in `<head>`
- [ ] `appHeader()` renders a `<header class="app-header">`
- [ ] `app-header.css` sets the header to 56px height
- [ ] `app-header.css` defines `.breadcrumb`, `.breadcrumb__item`, `.breadcrumb__item--current`, `.breadcrumb__sep` independently of old `breadcrumb.css`
- [ ] Desktop (>= 768px): logo, breadcrumb nav, and right-aligned actions visible; mobile section is `display: none`
- [ ] Mobile (< 768px): centered world name (and gear when applicable) visible; desktop section is `display: none`
- [ ] Mobile layout does NOT render a hamburger button
- [ ] Hamburger button from MCO-130 stub removed from `Navigation.kt`
- [ ] Breadcrumb uses `›` separator with class `breadcrumb__sep` and `--text-disabled` color
- [ ] Breadcrumb segments use IBM Plex Mono at `--text-sm`
- [ ] Current breadcrumb segment is `<span class="breadcrumb__item breadcrumb__item--current">` in `--text-primary`
- [ ] Prior breadcrumb segments are `<a class="breadcrumb__item">` links
- [ ] When `breadcrumbBlock` is null, no `<nav class="breadcrumb">` rendered
- [ ] When `worldName` is null, mobile displays "MC-ORG"
- [ ] Gear links to `/profile` when `worldId` and `projectId` are both null
- [ ] Gear links to `/worlds/:worldId/settings` when `worldId` is set and `projectId` is null
- [ ] Gear is NOT rendered (desktop or mobile) when `projectId` is set
- [ ] Gear routing implemented via private `gearHref()` helper — no duplicated when-expression
- [ ] Gear has `aria-label="Settings"` when rendered
- [ ] Old `createPage()` compiles and renders correctly
- [ ] Old `breadcrumb.css` not modified
- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all tests green
- [ ] No inline styles
- [ ] No Google Fonts in `pageShell()` pages
- [ ] Long text truncates with `text-overflow: ellipsis`

## Out of scope / deferred
- **Hamburger button and mobile drawer** — deferred; spec must define nav link list, overlay, close behaviour, animation
- **Unauthenticated page headers** — landing/login continue using old `createPage()`
- **Project detail mobile header** (`← [Project Name] [Plan|Exec]`) — belongs to project detail page rewrite
- **Project-context gear icon** — not rendered when `projectId` is set; will be added when epic is complete
- **Theme system** (overworld/nether/end) — deferred to cleanup phase
- **Notification badge** — not in IA nav spec
- **Admin link** — not in IA nav spec; admin pages use old TopBar
- **Page rewrites** — each is its own feature (MCO-135+)
- **Light theme** — Phase 3 per docs-product

## Tech lead review
Verdict: Changes recommended (all incorporated above)
Notes: Breadcrumb CSS class mismatch with old breadcrumb.css resolved by owning classes in app-header.css. app-header.css made a base stylesheet. Hamburger removal made explicit in sub-tasks. gearHref() helper extracted. Gear icon removed entirely for project context per product decision.
