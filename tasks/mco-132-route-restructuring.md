---
linear: MCO-132
parent-epic: MCO-128
status: approved
type: feature
created: 2026-03-04
---

# Route Restructuring

## Summary

Rewrite the URL structure from `/app/...` prefix to IA-compliant root-level URLs (`/worlds/...`, `/ideas/...`,
`/profile`, etc.). Rewrite the `Link` sealed interface, add a new route block with `BannedPlugin`, keep old `/app/...`
routes as 301 redirects for browser navigation, and update all hardcoded `/app/` URLs in templates, handlers, plugins,
and tests. No user-visible UI changes — this is a foundation feature that unblocks all Layer 2 page rewrites.

## User Value

URLs become shorter, cleaner, and match the IA hierarchy. Bookmarks and shared links to old `/app/...` URLs continue
working via 301 redirects. Every Layer 2 page rewrite (features 5-12) builds on the new URL structure from the start,
avoiding double-migration.

## Scope

**In:**

- Rewrite `Link` sealed interface — drop `/app/` prefix from all URLs
- New root-level route registration with `BannedPlugin`
- Merge HomeHandler into WorldHandler
- 301 redirects from old `/app/...` GET URLs to new equivalents
- Update all hardcoded `/app/...` strings in templates, handlers, plugins, and tests
- HTMX endpoints registered under new URL scheme

**Out:**

- Root `/` redirect logic (JWT `activeWorldId` check) — Feature 4b (MCO-133)
- Any UI changes or template rewrites — Layer 2 features
- Removing old `/app` route block — Feature 13 (MCO-142)
- Changes to AuthPlugin or DemoUserPlugin

## Current Behaviour

All authenticated routes live under `/app`:

```kotlin
route("/app") {
    install(BannedPlugin)
    appRouterV2()  // 7 handlers: Home, Profile, Admin, Notification, Invite, World, Idea
}
```

`Link` sealed interface produces URLs like `/app/worlds`, `/app/ideas`, `/app/profile`, etc.

HomeHandler registers a bare `get { }` under `/app` serving the worlds/invitations dashboard. WorldHandler serves world
detail (project list) at `/app/worlds/{worldId}`.

Additional hardcoded `/app` references:

- `handleLanding.kt`: `respondRedirect("/app")` for authenticated users
- `GetSignInPipeline.kt`: `customRedirectPath ?: "/app"` as auth redirect fallback
- `SessionPlugin.kt`: `cookie.path = "/app/ideas/create"` for idea wizard session

## Target Behaviour

**URL mapping:**

| Old URL                                      | New URL                                  |
|----------------------------------------------|------------------------------------------|
| `/app` (home)                                | `/worlds`                                |
| `/app/worlds`                                | `/worlds`                                |
| `/app/worlds/{worldId}`                      | `/worlds/{worldId}`                      |
| `/app/worlds/{worldId}/projects/{projectId}` | `/worlds/{worldId}/projects/{projectId}` |
| `/app/worlds/{worldId}/settings`             | `/worlds/{worldId}/settings`             |
| `/app/worlds/{worldId}/resources`            | `/worlds/{worldId}/resources`            |
| `/app/ideas`                                 | `/ideas`                                 |
| `/app/ideas/{ideaId}`                        | `/ideas/{ideaId}`                        |
| `/app/profile`                               | `/profile`                               |
| `/app/admin`                                 | `/admin`                                 |
| `/app/notifications`                         | `/notifications`                         |
| `/app/servers`                               | `/servers`                               |
| `/app/invites/{inviteId}/*`                  | `/invites/{inviteId}/*`                  |

**Link sealed interface:**

- `Link.Worlds.to` → `/worlds`
- `Link.Worlds.World(id).to` → `/worlds/{id}` (world home IS the project list — no `/projects` suffix needed)
- `Link.Ideas.to` → `/ideas`
- `Link.Profile.to` → `/profile`
- Add missing IA URL types: `/worlds/new`, `/worlds/{id}/projects/new`, `/worlds/{id}/roadmap`,
  `/worlds/{id}/projects/{id}/path`

**Redirect strategy:** GET requests to `/app/...` return 301 to new URL. No non-GET redirects — HTMX doesn't follow
standard 301 (browser intercepts). All template URLs updated in same task, so no HTMX request hits old URLs after
deployment.

**BannedPlugin installation:** Use `route("")` (empty string, no path segment added) as a grouping route inside
`routing { }` to install BannedPlugin without affecting `/auth/*`, `/test/*`, or `/account/*` routes. If `route("")`
doesn't work in Ktor 3.4.0, fall back to installing BannedPlugin inside `appRouterV2()` as the first line, wrapped in
`route("") { install(BannedPlugin); ... }`.

**HomeHandler merge:** Delete HomeHandler. Move its dashboard `get { }` into WorldHandler as the `GET /worlds` handler.
`appRouterV2()` drops the `HomeHandler` call.

## Technical Approach

**Module:** `mc-web` only.

**Files to modify:**

| File                    | Change                                                                       |
|-------------------------|------------------------------------------------------------------------------|
| `Link.kt`               | Drop `/app/` prefix from all URLs, add missing IA types                      |
| `mainRouter.kt`         | New route block with BannedPlugin via `route("")`, `/app` redirect catch-all |
| `HomeHandler.kt`        | Delete — merge into WorldHandler                                             |
| `WorldHandler.kt`       | Add `GET /worlds` handler (moved from HomeHandler)                           |
| `AppRouterV2.kt`        | Remove `HomeHandler` call                                                    |
| `handleLanding.kt`      | Change `respondRedirect("/app")` → `respondRedirect("/worlds")`              |
| `GetSignInPipeline.kt`  | Change `"/app"` fallback → `"/worlds"`                                       |
| `SessionPlugin.kt`      | Change `cookie.path` from `"/app/ideas/create"` → `"/ideas/create"`          |
| ~14 template files      | Replace hardcoded `/app/...` with `Link` references or new URLs              |
| 9 Kotlin test files     | Update `/app/...` URLs (~36 occurrences)                                     |
| 4 JavaScript test files | Update `/app/...` URLs                                                       |

**Redirect implementation:**

```kotlin
route("/app") {
    get("{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        val query = call.request.queryString()
        val redirect = "/$path${if (query.isNotEmpty()) "?$query" else ""}"
        call.respondRedirect(redirect, permanent = true)
    }
    get {
        call.respondRedirect("/worlds", permanent = true)
    }
}
```

**Database changes:** None.

## Sub-tasks

1. Rewrite `Link` sealed interface — drop `/app/` prefix, add missing IA URL types
2. Merge HomeHandler into WorldHandler — move dashboard `GET /worlds` handler, delete HomeHandler, update AppRouterV2
3. Update `mainRouter.kt` — new route block with BannedPlugin via `route("")`, add `/app` redirect catch-all
4. Update handler/plugin hardcoded URLs — `handleLanding.kt`, `GetSignInPipeline.kt`, `SessionPlugin.kt`
5. Update all template files with hardcoded `/app/...` strings (~14 files)
6. Update all test files — 9 Kotlin (~36 occurrences) + 4 JavaScript files, add redirect + BannedPlugin tests
7. Compile and run full test suite

## Acceptance Criteria

- [ ] All `Link` members produce URLs without `/app/` prefix
- [ ] `Link.Worlds.to` returns `/worlds`
- [ ] `Link.Worlds.World(1).to` returns `/worlds/1`
- [ ] `Link.Ideas.to` returns `/ideas`
- [ ] `Link.Profile.to` returns `/profile`
- [ ] HomeHandler deleted, its logic merged into WorldHandler
- [ ] New route block installs BannedPlugin — banned users get 403 on new URLs
- [ ] BannedPlugin does NOT apply to `/auth/*`, `/test/*`, `/account/*`
- [ ] GET `/app/worlds` → 301 to `/worlds`
- [ ] GET `/app/worlds/5/projects/3` → 301 to `/worlds/5/projects/3`
- [ ] GET `/app/ideas` → 301 to `/ideas`
- [ ] GET `/app` → 301 to `/worlds`
- [ ] Redirects preserve query strings
- [ ] `handleGetLanding()` redirects to `/worlds` (not `/app`)
- [ ] Auth sign-in fallback redirects to `/worlds` (not `/app`)
- [ ] Session cookie path is `/ideas/create` (not `/app/ideas/create`)
- [ ] All HTMX endpoints function at new URLs
- [ ] No hardcoded `/app/` strings in template files (excluding redirect code)
- [ ] `mvn clean compile` passes
- [ ] All tests pass (9 Kotlin + 4 JS test files updated)
- [ ] New tests: redirect coverage, BannedPlugin on new routes

## Out of Scope

| Item                                  | Reason                                | Where it belongs  |
|---------------------------------------|---------------------------------------|-------------------|
| Root `/` redirect (JWT activeWorldId) | Depends on Feature 3                  | MCO-133           |
| Removing `/app` route block entirely  | Cleanup after all pages rewritten     | MCO-142           |
| UI/template rewrites                  | Layer 2 features                      | MCO-134 – MCO-141 |
| Non-GET redirect for old `/app/` URLs | Templates updated in same task — moot | N/A               |

## Risks

1. **`route("")` compatibility** — Must verify `route("")` works as a no-op grouping in Ktor 3.4.0. Fallback: install
   BannedPlugin inside `appRouterV2()` wrapped in `route("")`.
2. **Cookie path change** — SessionPlugin cookie path change could invalidate existing sessions for idea creation
   wizards in progress at deploy time. Low severity — idea creation is short-lived and rare.

## Tech Lead Review

**Verdict:** Changes required (all incorporated above)

**Corrections applied:**

1. BannedPlugin wrapping uses `route("")` instead of `route("/")` to avoid applying to auth/test/account routes
2. `handleLanding.kt` redirect target updated from `/app` to `/worlds`
3. `GetSignInPipeline.kt` auth fallback updated from `/app` to `/worlds`
4. `SessionPlugin.kt` cookie path updated from `/app/ideas/create` to `/ideas/create`
5. HomeHandler merged into WorldHandler instead of creating conflicting `route("/worlds")` blocks
6. Test file count corrected to 9 Kotlin + 4 JavaScript files