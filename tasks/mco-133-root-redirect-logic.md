---
linear: MCO-133
parent-epic: MCO-128
status: approved
type: feature
created: 2026-03-09
---

# Root Redirect Logic

## Summary

Implement smart redirect logic at `/` that routes authenticated users to the correct landing page based on their JWT `activeWorldId` claim. Users with an active world go directly to that world's project list; users without one land on the world list. Unauthenticated users go to sign-in. Additionally, implement a route-level interceptor on world-scoped routes that automatically updates `activeWorldId` in the JWT when the user navigates into a different world.

## User Value

**Personas served:** All three — casual player, technical player, worker.

Instead of always landing on a generic home page and clicking through, the user opens the app and is immediately in their most recent world's project list. This is the "resume the world, not the project" principle from the IA spec. For casual players mid-game, this eliminates one full navigation step from the critical path.

## Scope

**Included:**
- `/` redirect handler: JWT check -> redirect to `/worlds/:activeWorldId/projects` or `/worlds`
- Unauthenticated `/` -> redirect to sign-in page (handled by AuthPlugin, no new code)
- Route-level interceptor on `/worlds/:worldId/...` that updates `activeWorldId` in JWT when it differs from the route's `:worldId`
- Update `handleGetLanding()` to use new logic
- Add `Link` variant for world projects list
- Integration tests for all redirect paths

**Excluded:**
- `activeWorldId` on `TokenProfile` — Feature 3 (MCO-131)
- Route restructuring (new IA URLs) — Feature 4a (MCO-132)
- Stale `activeWorldId` validation at redirect time — destination page handles 404/403
- Landing page redesign for unauthenticated users — Feature 12 (MCO-141)
- `HomeHandler` removal — Feature 13 (MCO-142)

## Behaviour

### `/` redirect

1. **Authenticated, has `activeWorldId`:** 302 redirect to `/worlds/:activeWorldId/projects`
2. **Authenticated, no `activeWorldId` (null):** 302 redirect to `/worlds`
3. **Not authenticated:** 302 redirect to `/auth/sign-in` (existing AuthPlugin behaviour — no code needed in handler)
4. **Stale `activeWorldId`** (world deleted or user lost access): Redirect proceeds; destination page handles 404/403

### `activeWorldId` interceptor

5. **User navigates to `/worlds/:worldId/...` where `:worldId` differs from `activeWorldId`:** Interceptor compares the two values. On mismatch, reissues the JWT cookie with the new `activeWorldId` in the response. Request proceeds normally — no redirect, no extra round-trip.
6. **User navigates to `/worlds/:worldId/...` where `:worldId` matches `activeWorldId`:** No-op. No cookie write.
7. **User navigates to `/worlds/:worldId/...` with null `activeWorldId`:** Treated as mismatch — cookie is reissued with the world ID.

This approach means **any link into a world-scoped page automatically updates `activeWorldId`** — no special wiring needed on individual pages or links.

### Token reissue mechanics

Read the current `TokenProfile` from call attributes, create a new `TokenProfile` with updated `activeWorldId`, pass through `CreateTokenStep`, then `AddCookieStep`. Use `pipelineResult` block, following the same pattern as the sign-in flow.

## Technical Approach

**Modules:** `mc-web` only. No DB changes.

**Key files:**
- `presentation/handler/handleLanding.kt` — rewrite to check `getUser().activeWorldId` and redirect
- `presentation/handler/WorldHandler.kt` — add interceptor in `worldRoutes()` inside the `route("/{worldId}")` block, after `WorldParamPlugin`
- `presentation/templated/common/link/Link.kt` — add variant for world projects list
- Cookie reissue uses `CreateTokenStep` + `AddCookieStep` in a `pipelineResult` block

**Notes:**
- `AuthPlugin` already handles unauthenticated users before the handler runs — no auth check needed in `handleGetLanding()`
- Existing `handleGetLanding()` runs its own JWT pipeline which is redundant with `AuthPlugin` — remove it
- `HomeHandler` at `/app` stays unchanged — cleanup deferred to Feature 13

**Skills to load during implementation:** `/docs-architecture`, `/docs-development`, `/docs-testing`

## Sub-tasks

1. Add a `Link` variant for the world projects list page (e.g., `Link.Worlds.World.Projects`)
2. Rewrite `handleGetLanding()` to check `getUser().activeWorldId` and redirect via `Link`. Remove the redundant pipeline JWT check.
3. Implement the `activeWorldId` interceptor in `WorldHandler.worldRoutes()` inside the `route("/{worldId}")` block: compare `getUser().activeWorldId` to route `:worldId`, reissue JWT cookie on mismatch using `CreateTokenStep` + `AddCookieStep` in `pipelineResult`.
4. Write integration tests: (a) `/` with `activeWorldId` set redirects to world projects, (b) `/` without `activeWorldId` redirects to `/worlds`, (c) navigating to a world-scoped route with different `activeWorldId` reissues the cookie, (d) navigating with matching `activeWorldId` does not reissue.

## Acceptance Criteria

- `GET /` with `activeWorldId=42` -> 302 to `/worlds/42/projects`
- `GET /` with null `activeWorldId` -> 302 to `/worlds`
- `GET /` with no JWT -> 302 to `/auth/sign-in`
- Navigating to `/worlds/99/projects` with `activeWorldId=42` reissues the auth cookie with `activeWorldId=99`
- Navigating to `/worlds/42/projects` with `activeWorldId=42` does NOT reissue the cookie
- After the interceptor fires for world 99, subsequent `GET /` redirects to `/worlds/99/projects`
- Banned user blocked on world-scoped routes (BannedPlugin)
- All existing tests pass
- Redirect URLs use `Link` sealed interface (not hardcoded strings)

## Out of Scope

| Item | Reason |
|------|--------|
| `TokenProfile.activeWorldId` field + JWT read/write | MCO-131 (Feature 3) |
| Link sealed interface rewrite to IA URLs | MCO-132 (Feature 4a) |
| Stale `activeWorldId` validation at redirect time | Destination handles it |
| Landing page redesign for unauthed users | MCO-141 (Feature 12) |
| `HomeHandler` removal | MCO-142 (Feature 13) |

## Dependencies

| Dependency | Status |
|-----------|--------|
| MCO-131 (Feature 3) — `TokenProfile.activeWorldId` + JWT claim handling | **Must be merged first** |
| MCO-132 (Feature 4a) — new IA route block + Link rewrite | **Must be merged first** |

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Feature 3 or 4a not ready | HIGH | Cannot start without both MCO-131 and MCO-132 |
| Cookie domain/path mismatch on JWT reissue | MEDIUM | Follow sign-in flow's cookie-setting pattern exactly |

## Tech Lead Review

**Verdict:** Changes recommended (incorporated above)

**Key corrections applied:**
1. URLs confirmed as `/worlds/:id/projects` (no `/app` prefix) — MCO-132 is a hard dependency
2. New `Link` variant needed for world projects list
3. Interceptor placed in `WorldHandler.worldRoutes()` after `WorldParamPlugin`
4. Cookie reissue uses `CreateTokenStep` + `AddCookieStep` in `pipelineResult` block
5. `AuthPlugin` already handles unauthed case — handler only sees authenticated users
6. `HomeHandler` stays, cleanup in Feature 13