---
linear-issue: MCO-141
parent-epic: MCO-128
status: approved
type: feature
phase: 1
created: 2026-05-07
---

# Feature 12: Remaining pages — Landing, Profile, Admin

## Summary

Catch-all rewrite of the three non-world-scoped pages not covered by other Layer 2 features:
unauthenticated landing (`/`), profile (`/profile`), and admin (`/admin`). Each migrates from
`createPage()` + Component classes to `pageShell()` + DSL + docs-product tokens.

This feature also **deletes the `/notifications` page UI** — the surface is not thought-through
enough to justify a rewrite, and a future notifications epic will redesign it from scratch.
Backend notification storage and dispatch are untouched.

No business logic changes for the three rewritten pages — same data, same flows, new chrome.

## User value

- **Blocker for Feature 13 cleanup.** Until these three pages migrate and the notifications UI is
  deleted, `createPage()` cannot be deleted.
- Landing is the only surface unauth visitors see — sets the "technical field notebook" first
  impression.
- Removes inconsistency for users who route through profile/admin occasionally.
- Removing the notifications page declutters navigation until the feature is properly scoped.

## Scope

### In

1. **Landing page rewrite** (`/`)
2. **Profile page rewrite** (`/profile`)
3. **Admin page rewrite** (`/admin`) — user table + world table, search, pagination
4. **Delete the notifications UI:**
   - Delete `presentation/templated/notification/` directory (page + row templates)
   - Remove `/notifications` route block from `AppRouterV2.kt`
   - Delete `pipeline/notification/GetNotificationsPipeline.kt` and the mark-read pipelines
     referenced by `NotificationHandler.kt`
   - Delete `Link.Notifications`, `ActiveLinks.NOTIFICATIONS`, `BreadcrumbBuilder.buildForNotifications()`
   - Remove the bell icon and unread-count badge wiring from legacy `TopBar.kt`
   - Drop `unreadNotificationCount` parameter from any *active* page constructor that still
     propagates it; the legacy `createPage()` may keep a defaulted parameter until Feature 13
   - Delete `GetUnreadNotificationCountStep.kt` and its `CacheManager` entry if nothing else reads
     unread counts (verify before deleting)
   - **Keep:** `Notification` domain model, DB table, and any code that *creates* notifications
5. **Port `dangerZone`** from `templated/common/dangerzone/DangerZone.kt` (legacy `LeafComponent`)
   to a DSL extension under `templated/dsl/`. Keep call sites identical where possible.
6. Add `featureCard` DSL helper only if landing layout requires it (single-use may not warrant it)
7. Breadcrumb wiring uses the DSL breadcrumb in `appHeader()` — not the legacy `BreadcrumbBuilder`

### Out of scope

- **Notifications page redesign** — split into a future notifications epic
- **Status / error pages (404, 500, banned, maintenance)** — separate Linear ticket; out of scope here
- **`/servers` route + `Link.Servers` + `ActiveLinks.SERVERS`** — known residue, defer to Feature 13
- **Component.kt / LeafComponent removal** — Feature 13 (this feature unblocks but does not perform)
- Profile editing (display name / avatar — stubs today, no driver)
- Implementing admin stub handlers (ban / delete actions) — render only
- Auth UX changes
- Sign-out flow changes

## Behaviour

### Landing (`/`, unauthenticated)

- Brand bar (no `appHeader`) with logo only
- Hero: title (IBM Plex Mono `--text-heading`, `--text-primary`), one-line tagline (Inter)
- `.btn--primary` "Sign in with Microsoft" → Microsoft OAuth URL
- Three feature cards (`.surface` + `--border`); icon + title + body. Stack on mobile.
- Container: `.container` (1080px max). Section padding `--space-6` vertical.

### Profile (`/profile`)

- `appHeader()` with single-segment `Profile` breadcrumb (DSL breadcrumb, not legacy `BreadcrumbBuilder`)
- Page heading: `[Username]'s Profile`
- ACCOUNT section: `.section-label`, prose, `.btn--secondary` Sign out → `/auth/sign-out`
- Danger zone (ported `dangerZone` DSL): `.btn--danger` Delete account triggers existing
  typed-confirm modal → `DELETE /account`

### Admin (`/admin`, superadmin only)

- `appHeader()` with single-segment `Admin` breadcrumb
- Page heading: `Admin Dashboard`, prose
- **User Management:** section label, live-search input (HTMX → `/admin/users/search`),
  `.data-table` (User, Username, Email, Status, Joined, Last Active, Actions), prev/next
  pagination, "Showing X-Y of Z (Page A of B)"
- **World Management:** same pattern (Name, Version, Projects, Members, Created On, Actions)
- Mobile: `.data-table` collapses to stacked cards via CSS — table elements (`<table>`, `<tr>`,
  `<td>`) remain in DOM at all viewports
- Empty state: zero-result "No matches" row in tbody
- Role/status surfaces: plain text for roles; `.badge--blocked` only for `BANNED`

### Notifications deletion

- `GET /notifications` returns 404 (route removed; default Ktor 404)
- Bell / unread badge removed from chrome
- HTMX endpoints `PATCH /notifications/read` and `PATCH /notifications/{id}/read` removed
- `Notification` model, DB table, and dispatch code stay

## Technical approach

- All work in `mc-web`. No domain or pipeline changes for the three rewritten pages.
- New DSL: port `dangerZone`; add `featureCard` only if needed.
- Page templates rewritten in `presentation/templated/{landing,profile,admin}/`.
- **Admin search HTMX contract:** existing endpoints (`SearchManagedUsersPipeline`,
  `SearchManagedWorldsPipeline`) return `<tbody id="admin-{users,worlds}-rows">…</tbody>` plus an
  out-of-band `<td hx-swap-oob="innerHTML:#pagination-info-{users,worlds}">…</td>`. The swap
  target stays `<tbody>` at all viewports; mobile card-collapse is **CSS-only** (display rules on
  table elements). Response shape does **not** branch by viewport.
- New admin row markup must continue to point at the existing handler URLs (or no `hx-*` if
  current code has none — preserve that until the stub handlers are implemented).
- No DB changes.
- Skills during impl: `/docs-product`, `/docs-architecture`, `/docs-htmx`, `/docs-development`.

## Sub-tasks

1. **Port `dangerZone`** LeafComponent → DSL extension; update existing call sites
2. **Landing rewrite** + integration test (unauth render)
3. **Profile rewrite** + integration test (uses ported `dangerZone`)
4. **Admin rewrite** + `.data-table` integration + search swap + pagination + integration test
   (verify `<tbody>` swap target unchanged across viewports)
5. **Notifications UI deletion** — route, templates, pipelines, `Link.Notifications`,
   `ActiveLinks.NOTIFICATIONS`, `buildForNotifications`, bell icon in legacy TopBar,
   `unreadNotificationCount` from active call sites (legacy `createPage` may retain defaulted
   param until F13), `GetUnreadNotificationCountStep` if unused
6. **Cleanup pass** — `grep -rn 'unreadNotificationCount\|Link\.Notifications\|ActiveLinks\.NOTIFICATIONS\|buildForNotifications'`
   returns no live references; `mvn clean compile` confirms

## Acceptance criteria

- Landing/profile/admin templates use `pageShell()` (and `appHeader()` where authenticated); no
  `createPage()` imports remain in those three files
- `GET /notifications` returns 404; nav has no notifications affordance
- Compile-time check: `unreadNotificationCount`, `Link.Notifications`, `ActiveLinks.NOTIFICATIONS`,
  `buildForNotifications` have no live references in `mc-web` (legacy `createPage` may carry a
  defaulted `unreadNotificationCount = 0` until Feature 13)
- Admin tables collapse to cards on mobile via CSS only; HTMX swap target stays `<tbody>` at all
  viewports; pagination boundaries disable
- `mvn clean compile` and `mvn test` pass; integration tests cover render + auth (admin needs
  superadmin) + 404 on `/notifications`
- 360px and 1280px screenshots of landing/profile/admin attached to Linear issue
- No inline `style=`; all colors via CSS vars

## Risks

- **Plumbing churn from `unreadNotificationCount` removal.** Touches ~9 files
  (`Page.kt`, `TopBar.kt`, `WorldPage.kt`, `ProfilePage.kt`, `ProjectPage.kt`, `AdminPage.kt`,
  `NotificationTemplates.kt`, `CacheManager.kt`, `GetUnreadNotificationCountStep.kt`).
  Implementer should do this as a single follow-up commit after the three pages migrate, running
  `mvn clean compile` after each removal.
- **Notifications backend orphaning.** Deleting the UI but keeping notification *writes* leaves
  an unused dispatch path. Acceptable — future notifications epic will reuse it.
- **`Component.kt` cannot be deleted yet.** Many other helpers (`linkComponent`, `iconButton`,
  legacy `topBar`) still extend `LeafComponent`. Feature 12 unblocks Feature 13 only for the
  three pages it touches.
- **Admin HTMX target divergence on mobile.** Strictly enforced by acceptance criteria: response
  shape does not change by viewport.

## Tech lead review

**Verdict:** Changes recommended (incorporated above).

**Key corrections applied:**

1. `dangerZone` already exists as a `LeafComponent`; spec now says "port", not "create"
2. Notifications deletion expanded to enumerate all references: `Link.Notifications`,
   `ActiveLinks.NOTIFICATIONS`, `BreadcrumbBuilder.buildForNotifications`, `unreadNotificationCount`
   plumbing, `GetUnreadNotificationCountStep`
3. Admin search HTMX response shape pinned: `<tbody>` swap target unchanged across viewports;
   mobile collapse is CSS-only
4. `/servers` route + `Link.Servers` flagged as known residue for Feature 13
5. `Component.kt` removal explicitly Feature 13's job, not this feature's
6. Breadcrumbs use DSL `appHeader()` breadcrumb, not legacy `BreadcrumbBuilder`
7. Tech-lead's claim that Feature 5 (`pageShell`/`appHeader`) had not landed was incorrect —
   verified present in `templated/dsl/Layout.kt:8` and `templated/dsl/Navigation.kt:41`, used
   by world list, project list, project detail pages
