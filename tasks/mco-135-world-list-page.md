---
linear-issue: MCO-135
status: approved
type: feature
created: 2026-03-11
depends-on:
  - MCO-129  # Feature 1: Fonts + CSS tokens
  - MCO-130  # Feature 2: DSL foundation
  - MCO-131  # Feature 3: JWT activeWorldId
  - MCO-132  # Feature 4a: Routes
  - MCO-133  # Feature 4b: Root redirect
  - MCO-134  # Feature 5: Nav chrome + page shell
---

# World List Page

## Summary
Rewrite the `/worlds` page using the new DSL template system (`pageShell()` + `appHeader()`) and design tokens.
Replaces old Component-based templates with new DSL extension functions rendering world cards, an empty state,
and the "Create World" flow. Clicking a world card navigates to `/worlds/:worldId/projects` and sets
`activeWorldId` in JWT via the existing `UpdateActiveWorldPlugin`. After this feature, `GET /worlds` always
renders `worldListPage()` — the old `homePage()` is unreachable.

## User Value
All three personas pass through this page.

- **Casual player:** Large tappable world cards, mobile-friendly, no Google Fonts latency.
- **Technical player:** World name, version, project count, and completion progress visible at a glance.
- **Worker:** One tap to reach their active world's project list.

## Scope
**Included:**
- `worldListPage()` using `pageShell()` + `appHeader()` (no breadcrumb, gear → `/profile`)
- World cards (`.card`) with: name, version text (`--text-muted`), description (2-line clamp), progress bar, completion count
- Empty state when user has no worlds (`.empty-state` with "Create World" CTA)
- `modalForm()` convenience wrapper in `Modal.kt` — wraps form boilerplate + HTMX submission attributes
- "Create World" using `modalForm()` with name, description, version fields
- World search via HTMX (`hx-get="/worlds/search"`, 300ms debounce, swaps card list)
- `pageShell()` gains `stylesheets: List<String> = emptyList()` param for page-specific CSS
- `world-card.css` for card-specific styles (hover, description clamp, progress label)
- Demo user guard on create world (existing behaviour preserved)
- `handleGetHome()` simplified — drops invitation/notification fetching (deferred to Feature 6b)
- `handleSearchWorlds()` updated to return new-DSL fragments (safe: old `/worlds` route fully replaced)

**Excluded:**
- Pending invitations — deferred to Feature 6b (needs spec)
- Sort dropdown — not in IA spec for Phase 1
- Pagination — world counts per user expected to be small
- World deletion — belongs in Feature 11 (world settings)
- Old template deletion — Feature 13 cleanup

## Behaviour

### User has worlds
1. `appHeader()` renders with no breadcrumb, gear → `/profile`.
2. Search input + "Create World" `.btn--primary` in a flex row.
3. World cards in a list, each: world name (IBM Plex Mono, weight 600), version text (`--text-muted`),
   description (Inter, 2-line clamp), progress bar (`.progress--lg`), label ("3 of 8 projects completed").
4. Card hover: border-color → `#3a4060`, background → `--bg-raised`.
5. Each card is an `<a>` linking to `/worlds/:worldId/projects`. `UpdateActiveWorldPlugin` sets `activeWorldId` in JWT.
6. World with 0 projects: progress label reads "No projects yet".
7. Long names truncate with `text-overflow: ellipsis`.

### User has no worlds
1. Centered `.empty-state`: heading "No worlds yet", body text, "Create World" `.btn--primary`.
2. No search bar rendered.

### Create World modal
1. `modalForm()` wraps the form with action, method, and HTMX submission attributes baked in.
2. Fields: World Name (required, 3–100 chars), Description (optional, max 500 chars), Version (required, dropdown of supported versions from `GetSupportedVersionsStep`).
3. Submit: `hx-post="/worlds"`, `hx-target="#worlds-content"` — swaps the content area on success.
4. Validation errors render inline below each field.
5. Demo users see a disabled state with explanatory text.

### Search
1. `hx-get="/worlds/search?query=..."` with 300ms debounce, swaps `#world-card-list`.
2. `handleSearchWorlds()` returns new-DSL world card fragments. Old out-of-band `#home-worlds-count` swap removed (old page no longer reachable).

## Technical Approach

**Module:** `mc-web` only. No new pipeline steps. No database changes.

**Files created:**
- `presentation/templated/dsl/WorldCard.kt` — `worldCard()`, `worldCardList()`
- `presentation/templated/dsl/pages/WorldListPage.kt` — `worldListPage(user, worlds, supportedVersions)`
- `resources/static/styles/components/world-card.css`

**Files modified:**
- `presentation/templated/dsl/Layout.kt` — add `stylesheets: List<String> = emptyList()` to `pageShell()` (backward compatible — all existing callers compile unchanged)
- `presentation/templated/dsl/Modal.kt` — add `modalForm()` convenience wrapper with action, method, and HTMX attributes
- `presentation/templated/dsl/EmptyState.kt` — implement `emptyState()` (currently a stub) per docs-product spec
- `presentation/handler/WorldHandler.kt` — simplify `handleGetHome()` (drop invitation/notification calls, call `worldListPage()`); update `handleSearchWorlds()` and `handleCreateWorld()` to return new-DSL fragments

**No files deleted.** Old templates remain until Feature 13.

**Skills to load:** `/docs-product`, `/docs-css`, `/docs-htmx`, `/docs-development`

## Sub-tasks
1. Implement `emptyState()` in `EmptyState.kt` with `.empty-state`, heading, body, optional CTA block per docs-product spec
2. Add `stylesheets: List<String> = emptyList()` param to `pageShell()` in `Layout.kt`
3. Add `modalForm()` convenience wrapper in `Modal.kt` — bakes in form boilerplate and HTMX submission attributes
4. Create `WorldCard.kt` + `world-card.css` — card layout, hover states, progress label, description clamp
5. Create `WorldListPage.kt` — full page composition with `#worlds-content` and `#world-card-list` element IDs; HTMX wired
6. Update `WorldHandler` — simplify `handleGetHome()`, update `handleSearchWorlds()` and `handleCreateWorld()` to return new-DSL output

## Acceptance Criteria
- [ ] `/worlds` renders with `pageShell()` — no Google Fonts, no old CSS tokens
- [ ] `appHeader()` renders: no breadcrumb, gear → `/profile`
- [ ] World cards use `.card` class, show name, version, description, progress bar, completion count
- [ ] Card hover: border `#3a4060`, background `--bg-raised`
- [ ] Each card links to `/worlds/:worldId/projects`; `activeWorldId` set in JWT on navigation
- [ ] Empty state renders with "No worlds yet" heading and "Create World" CTA when user has no worlds
- [ ] "Create World" modal opens with form fields, submits via `hx-post="/worlds"`, re-renders list on success
- [ ] Validation errors render inline in modal
- [ ] Demo users see disabled create with explanatory text
- [ ] Search fires HTMX request and swaps `#world-card-list`
- [ ] World with 0 projects shows "No projects yet"
- [ ] Long names truncate with ellipsis
- [ ] Mobile: cards full-width, 44px minimum tap target
- [ ] No inline styles
- [ ] `mvn clean compile` passes; `mvn test` passes; old templates still compile

## Out of Scope
| Item | Where it belongs |
|------|-----------------|
| Pending invitations | Feature 6b (needs spec) |
| Sort dropdown | Phase 2 backlog |
| Pagination | Backlog |
| World deletion | Feature 11 (MCO-140) |
| Old template deletion | Feature 13 (MCO-142) |
