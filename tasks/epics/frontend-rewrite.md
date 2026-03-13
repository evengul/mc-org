---
linear-epic: MCO-128
linear-children:
  - MCO-129  # Feature 1: Self-hosted fonts + CSS design tokens
  - MCO-130  # Feature 2: Template DSL foundation
  - MCO-131  # Feature 3: JWT activeWorldId + view preference DB
  - MCO-132  # Feature 4a: Route restructuring
  - MCO-133  # Feature 4b: Root redirect logic
  - MCO-134  # Feature 5: Navigation chrome + page shell
  - MCO-135  # Feature 6: World list page
  - MCO-136  # Feature 7a: Project list page — execute view + empty state + create flow
  - MCO-150  # Feature 7b: Project list page — plan view + toggle
  - MCO-137  # Feature 8: Idea Hub pages
  - MCO-138  # Feature 9: Project detail — execute view
  - MCO-139  # Feature 10a: Project detail — plan view resource table
  - MCO-153  # Feature 10b: Project detail — resource detail panel
  - MCO-140  # Feature 11: World settings page
  - MCO-141  # Feature 12: Remaining pages
  - MCO-142  # Feature 13: Delete old system (cleanup)
  - MCO-143  # Feature 14: docs-frontend skill
status: approved
type: feature
created: 2026-03-04
---

# Frontend Rewrite — Technical Field Notebook

## Goal

Replace the entire frontend — CSS system, template architecture, layout, components, page templates,
and routing — with the new "technical field notebook" design system defined in docs-ia and docs-product.

When done:

- New dark-only design tokens (IBM Plex Mono + Inter, `--bg-base` #0F1117 palette, 1080px max width, 4px spacing grid)
- New DSL-based template architecture (no Component class hierarchy)
- IA-compliant URL structure (`/worlds/:worldId/projects/...`)
- Self-hosted fonts (no CDN dependency)
- New `docs-frontend` skill replacing deprecated `docs-css`
- No legacy CSS files, template classes, or route code remains

## Product Brief

**Personas affected:**

- **Casual player** (highest impact): mobile-first resource tracking usable one-handed while mining
- **Technical player**: dense resource table in plan view, per-project toggle persistence, breadcrumb nav
- **Worker**: execute view clarity and mobile header simplification

**User value:** Removes UI friction from the primary use case: open app mid-game, find project, tap counter.
The current SaaS dashboard paradigm forces unnecessary navigation. The "technical field notebook"
aesthetic sets correct expectations — precise and dense, not friendly and rounded.

**Phase compliance:** All included work is Phase 1. Phase 2/3 features explicitly excluded.

## Current State

**CSS (44 files, ~5,800 lines):**

- `root.css` (609 lines): Roboto Mono, em-based spacing, three themes (overworld/nether/end), `--clr-*` tokens, 1200px
  max-width
- 28 component CSS files, 10 page-specific CSS files
- Entry: `styles.css` imports everything via `PageStyle` enum

**Templates (84 Kotlin files):**

- Component hierarchy: `Component` interface -> `LeafComponent`/`NodeComponent` -> `addComponent()` pattern
- 26 files use hierarchy directly (67 occurrences)
- Key offenders: `GenericButton` (12 constructor params), `Modal` (7 params + subclasses)
- `createPage()` wraps full HTML doc with Google Fonts, TopBar, breadcrumbs

**Routes:** All under `/app/` prefix. 7 handler classes in `AppRouterV2.kt`.

**JWT:** Carries sub, minecraft_username, minecraft_uuid, display_name, roles. No `activeWorldId`.

**Navigation:** TopBar with Home, Idea Bank, Servers, Admin, Notifications, Profile. No mobile hamburger. No
world-scoped nav.

## Target State

**CSS:** New design tokens from docs-product. Dark-only. IBM Plex Mono (`--font-ui`) + Inter (`--font-body`) self-hosted
as woff2. New component classes per docs-product spec (`.btn--primary`, `.toggle`, `.badge--done`, `.resource-row`,
`.data-table`, `.callout`, `.app-header`, etc.). No page-specific CSS — component composition replaces page-level
styles.

**Templates:** All components are Kotlin HTML DSL extension functions on `FlowContent`/`Tag`. No `Component` interface,
no `addComponent()`. Components compose via lambda blocks:

```kotlin
appHeader(worldName = "Survival") {
    breadcrumb("Worlds", "/worlds") / current("Iron Farm")
}
primaryButton { +"Save" }
modal("delete-confirm", "Delete Project") {
    p { +"This cannot be undone." }
    modalActions { ghostButton { +"Cancel" }; dangerButton { +"Delete" } }
}
```

**Routes:** IA-compliant URLs (no `/app/` prefix). `activeWorldId` in JWT. Per-project view preference in DB.

**Navigation:** Desktop: `[Logo] Worlds > [World] ... Ideas [gear]`. Mobile: `[hamburger] [World Name] [gear]`.

## Migration Strategy

**Foundation-first, then parallel page rewrites with old routes co-existing.**

**Layer 1 — Foundations (no user-visible changes):**

1. Self-host fonts, add new CSS tokens alongside old ones. Both loaded simultaneously. Different token names prevent
   conflicts (`--bg-base` vs `--clr-bg-default`).
2. Build new DSL component kit alongside old Component classes. Both coexist.
3. Add `activeWorldId` to JWT (optional, nullable). Add view preference DB table. Both additive.
4. Add new IA routes alongside old routes (dual-mount). Old routes redirect to new.

**Layer 2 — Page rewrites (parallelisable):**
Each page rewritten using new DSL, new CSS, new routes. Old pages continue working on old routes until rewritten. HTMX
endpoints use new URL scheme from the start.

**Layer 3 — Cleanup (after all pages done):**
Delete old Component hierarchy, old CSS, old routes, Google Fonts refs, theme system.

**Safety:** At every step, `mvn clean compile` and all tests pass. Each commit is shippable. Rollback: revert commit.

## Child Features

### Layer 1: Foundation

**Feature 1: Self-hosted fonts + CSS design tokens**

- Bundle IBM Plex Mono (Regular, Medium, SemiBold) + Inter (Regular, Medium) as woff2 in `/static/fonts/`
- New CSS with `@font-face` declarations + all docs-product tokens (colors, typography, spacing, components)
- Update `PageStyle` enum to load both old and new
- Depends: none
- Skills: `/docs-product`

**Feature 2: Template DSL foundation**

- New package `presentation/templated/dsl/` with extension functions: Buttons.kt, Modals.kt, Navigation.kt, Layout.kt,
  DataTable.kt, ProgressBar.kt, Badge.kt, Toggle.kt, TaskList.kt, ResourceRow.kt, EmptyState.kt, Callout.kt
- `FlowContent`/`Tag` extensions emitting HTML directly
- Plan/execute toggle component (CSS-only, not wired to persistence yet)
- Do NOT delete old Component files — both coexist
- Unit tests for key DSL functions
- Depends: feature 1
- Skills: `/docs-development`, `/docs-architecture`, `/docs-product`
- Note: Scope DSL kit to components needed for first page rewrite (feature 5), then grow incrementally

**Feature 3: JWT activeWorldId + view preference DB**

- Add `activeWorldId: Int? = null` as LAST parameter on `TokenProfile` (in mc-domain) — preserves backward compatibility
- In `ConvertTokenStep`: read as nullable claim (`jwt.getClaim("active_world_id")?.asInt()`). Do NOT add to
  `withClaimPresence()`
- In `CreateTokenStep`: write conditionally (`input.activeWorldId?.let { withClaim("active_world_id", it) }`)
- Update `TestDataFactory`, `WithUser`, and existing token tests
- Flyway migration `V2_30_0__create_user_project_view_preference.sql`
- Pipeline steps: `SetActiveWorldStep`, `GetViewPreferenceStep`, `SetViewPreferenceStep`
- HTMX endpoint for toggling view preference
- Compile from root (`mvn clean compile`), not just mc-web
- Depends: none
- Skills: `/add-migration`, `/add-step`, `/add-endpoint`, `/docs-testing`

**Feature 4a: Route restructuring**

- Rewrite `Link` sealed interface to new IA URLs (drop `/app/` prefix)
- New route registration matching IA URL structure alongside old routes
- New route block MUST install `BannedPlugin` (mirroring existing `/app` block)
- Permanent redirects from old `/app/...` URLs to new URLs
- HTMX endpoints use new URL scheme from the start
- Depends: none (can parallelise with features 1 and 3)
- Skills: `/docs-architecture`, `/docs-htmx`

**Feature 4b: Root redirect logic**

- Implement `/` redirect: check JWT `activeWorldId`, redirect to `/worlds/:id/projects` or `/worlds`
- Handler that sets `activeWorldId` in JWT when navigating into a world
- Depends: feature 3 (needs activeWorldId in JWT)

### Layer 2: Page Rewrites

**Feature 5: Navigation chrome + page shell**

- Rewrite `createPage()`/`TopBar`/`BreadcrumbComponent` using new DSL
- `appHeader()` extension function per docs-product spec (56px, breadcrumb nav, desktop + mobile variants)
- `pageShell()` replaces `createPage()` for new pages
- Old `createPage()` remains for unrewritten pages
- Theme system (overworld/nether/end) untouched — deferred to cleanup
- Depends: features 1, 2
- Skills: `/docs-product`, `/docs-htmx`, `/docs-architecture`

**Feature 6: World list page**

- Rewrite `/worlds` page with new design
- World cards, empty state, "Create world" flow
- Sets `activeWorldId` in JWT when clicking into a world
- Pending invitations display deferred to Feature 6b (see below)
- Depends: features 4a, 5

**Feature 6b: Pending invitations on user front page** *(needs spec — not yet planned)*

- Pending world invitations should be shown on the user's front page (likely `/worlds` or a dedicated notifications area)
- Acceptance/decline flow with new DSL and design tokens
- [NOTE: Needs full spec before implementation. Determine: is this a section on `/worlds`, a separate `/invitations` page, or part of a notifications feature? Coordinate with Feature 12 (remaining pages).]

**Feature 7a: Project list page — execute view + empty state + create flow**

- Rewrite `/worlds/:worldId/projects` using new DSL and design tokens
- `GET /worlds/:worldId` redirects to `/worlds/:worldId/projects`
- Project cards (execute view): project name, status badge (NOT_STARTED/IN_PROGRESS/DONE — no BLOCKED in Phase 1),
  task progress text, resource progress bar (6px), next incomplete task
- Resource aggregates and next task fetched via a single extended query (JOIN/subquery on the project list query — no N+1)
- Empty state: two equal-weight cards ("Plan your own project" → create modal / "Browse community ideas" → `/ideas`)
- Create project modal (name + description + type)
- Depends: features 3, 5, 6

**Feature 7b: Project list page — plan view + toggle**

- Plan view project cards: resource definition count, dependency summary (blocked-by / blocks counts)
- Session-level Plan/Execute toggle wired to HTMX fragment swap
- Toggle preference stored in a true session cookie (no `Max-Age`) — not the DB view preference table
- `GET /worlds/:worldId/projects/list-fragment?view=plan|execute` fragment endpoint
- Depends: feature 7a

**Feature 8: Idea Hub pages**

- Rewrite `/ideas` and `/ideas/:ideaId`
- Filterable card grid, version filter, search
- Import-to-world with version mismatch modal
- Depends: feature 5
- Can parallelise with: features 6, 7a

**Feature 9: Project detail — execute view**

- Rewrite execute view at `/worlds/:worldId/projects/:projectId`
- Resource rows with counter increments (+1/+64/+1782), progress bars, source labels
- Task checklist below resources
- Plan/execute toggle wired to DB persistence
- Depends: features 3, 5, 7a

**Feature 10: Project detail — plan view (Phase 1 read-only)**

- Read-only resource table (Item | Qty | Source) using `.data-table`
- Dependencies section: blocked-by / blocks with specific resource edges
- Tasks collapsed by default
- "Generate path" / "View path" CTAs (disabled if no path exists)
- **Empty state:** "No resources defined" + CTAs: "Switch to Execute" and "Browse Idea Hub"
- No inline edit (Phase 2)
- Depends: feature 9 (same page, toggle switches views)

**Feature 11: World settings page**

- Rewrite `/worlds/:worldId/settings`
- General, members, invitations tabs
- Depends: feature 5

**Feature 12: Remaining pages**

- Landing, profile, notifications, admin pages
- Lower-priority, follow same pattern
- Depends: feature 5

### Layer 3: Cleanup

**Feature 13: Delete old system**

- Delete `Component.kt`, `LeafComponent.kt`, `NodeComponent.kt`, `ComponentHelpers.kt`
- Delete all old Component-based classes (TopBar, GenericButton, Modal, etc.)
- Delete all 44 old CSS files
- Remove Google Fonts `<link>` tags and `preconnect` hints
- Remove old route registrations and `/app/` redirects
- Remove `createPage()` (replaced by `pageShell()`)
- Remove overworld/nether/end theme CSS and theme-cycling script
- Clean up `PageStyle` enum
- Depends: ALL Layer 2 features complete

**Feature 14: docs-frontend skill**

- Create new `docs-frontend` skill documenting: CSS tokens, component classes, DSL usage patterns, layout utilities,
  HTMX conventions
- Delete `docs-css` skill
- Update `CLAUDE.md` skills table
- Depends: features 1-3 (content), all Layer 2 (final class names)

## Dependency Graph

```
Feature 1 (Fonts + CSS) ─────┐
                              ├─> Feature 2 (DSL foundation) ─> Feature 5 (Nav + shell) ─┐
Feature 3 (JWT + DB) ────────┤                                                            ├─> Feature 7a (Project list — execute) ─> Feature 7b (Project list — plan/toggle)
                              │                                                            │       └─> Feature 9 (Execute) ─> Feature 10 (Plan)
Feature 4a (Routes) ─────────┤                                                            ├─> Feature 6 (World list) ──┘
                              └─> Feature 4b (/ redirect) [after 3]                       ├─> Feature 8 (Idea Hub)
                                                                                           ├─> Feature 11 (Settings)
                                                                                           └─> Feature 12 (Remaining)
                                                                                                       │
                                                                                       All Layer 2 ────┘─> Feature 13 (Cleanup) ─> Feature 14 (docs-frontend)
```

**Parallelisation:**

- Features 1, 3, 4a: all start in parallel (zero file overlap)
- Feature 2 starts after 1; Feature 4b starts after 3
- Features 6, 8, 11, 12 all parallelise after feature 5
- Feature 7a unblocks Feature 7b; both unblock Feature 9
- Features 9, 10 are sequential (same page)

## Deferred / Out of Scope

| Item                                   | Why excluded             | Where it belongs                 |
|----------------------------------------|--------------------------|----------------------------------|
| Inline resource definition (plan view) | Phase 2 feature          | "Inline Resource Planning" epic  |
| Production path generation             | Phase 2 feature          | "Production Path" epic           |
| Partial dependency notices             | Phase 2 feature          | "Execute View Enhancements" epic |
| Roadmap dependency table               | Phase 2 feature          | "Roadmap" epic                   |
| DAG visualisation                      | Phase 3 feature          | Future epic                      |
| Task assignment                        | Phase 3 feature          | Future epic                      |
| Light theme                            | Phase 3 per docs-product | Future epic                      |
| Lucide icon migration                  | Independent              | Small standalone epic            |
| Mobile hamburger drawer JS             | Needs JS beyond CSS      | Follow-up after Feature 5        |

## Risks

| Risk                                                | Severity | Mitigation                                                                    |
|-----------------------------------------------------|----------|-------------------------------------------------------------------------------|
| DSL foundation scope — critical path bottleneck     | HIGH     | Scope initial kit to components needed for feature 5, grow incrementally      |
| CSS specificity conflicts during dual-system period | MEDIUM   | Different token/class names prevent most conflicts. Watch during features 4-7 |
| JWT backward compatibility during rollout           | MEDIUM   | `activeWorldId` is nullable with default `null`. Old tokens work unchanged    |
| Route dual-mounting + HTMX endpoints                | MEDIUM   | Use new URL scheme from start, redirect old paths. Don't try URL-agnosticism  |
| Project page complexity (feature 9+10)              | MEDIUM   | Split into sub-features at implementation time if needed                      |
| Three-theme removal is user-visible                 | LOW      | Accepted as part of redesign. Dark-only for Phase 1 per docs-product          |
| Triple font loading during transition               | LOW      | Temporary (~200KB extra). Removed in cleanup. Use `font-display: swap`        |

## Tech Lead Review

**Verdict:** Changes recommended (incorporated above)

**Key corrections applied:**

1. `TokenProfile.activeWorldId` added as last nullable param with default — backward compatible
2. `ConvertTokenStep` reads as nullable claim, NOT in `withClaimPresence()`
3. Feature 4 split into 4a (routes, no deps) and 4b (redirect, depends on 3) for more parallelism
4. BannedPlugin must be installed on new IA route block
5. Theme removal explicitly deferred to Feature 13
6. HTMX endpoints use new URL scheme from start, old paths redirect
7. Migration numbering: `V2_30_0`

## Next Steps

1. **Start features 1, 3, and 4a in parallel** — zero file overlap, maximum parallelism
2. **Then features 2 and 4b** — once their dependencies land
3. **Feature 5 (navigation + page shell)** — gateway to all page rewrites, first integration test
4. **Parallelise page rewrites aggressively** — features 6, 8, 11, 12 simultaneously
5. **Features 9-10 (project detail)** — most complex, run past PO for review
6. **Feature 13 (cleanup) last** — only when every page is confirmed working
7. **Feature 14 (docs-frontend)** — after cleanup

For each child feature, run `/new-task` with the feature description from this spec.
