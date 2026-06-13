---
linear-issue: MCO-156
parent-epic: MCO-128
status: approved
type: feature
phase: 1
created: 2026-05-08
---

# Feature 12b: Status / error pages

## Summary

Create new minimalist HTML status-page templates (404 Not Found, 500 Server Error, Banned)
using `pageShell()` + DSL + docs-product tokens, and wire them into the Ktor `StatusPages`
plugin and the `BannedPlugin` so the app renders a designed page in those states instead of
plain text. These templates do not use `appHeader()` — they are intentionally chrome-less,
with a logo-only brand bar at top.

The Linear ticket framing of "migrate from `createPage()`" is stale: a repo-wide grep returns
zero matches for those template names. Today the only error surfaces are plain-text responses
in `Routing.kt:11-20` and `RolePlugins.kt:65,80`, plus Ktor's default 404. So this feature
*creates* status templates rather than migrating any. The cleanup contract for Feature 13
(MCO-142) is unchanged either way.

## User value

- Every signed-in user hits 404/500 occasionally; a banned user is the worst-case first
  impression. None are well-served by plain text today.
- Replaces `500: io.ktor.server.…NoSuchElementException` (which leaks the exception message —
  small info-disclosure) with a designed page that names what went wrong in plain language
  and offers a next step.
- Unblocks Feature 13 cleanup honestly: status pages are the only surface where the legacy
  plain-text response still exists alongside the new design system.

## Scope

### In scope

1. **404 (Not Found) template** — `presentation/templated/error/NotFoundPage.kt`
2. **500 (Server Error) template** — `presentation/templated/error/ServerErrorPage.kt`
3. **Banned template** — `presentation/templated/error/BannedPage.kt`
4. **`errorPageLayout` DSL helper** — `presentation/templated/error/ErrorPageLayout.kt`.
   Chrome-less brand-bar shell with centered surface card + heading + body + optional CTA.
   All three templates compose around it.
5. **Wiring 404** — add a `status(HttpStatusCode.NotFound) { call, _ ->
   call.respondHtml(notFoundPage(), HttpStatusCode.NotFound) }` handler in `Routing.kt`'s
   `StatusPages` block.
6. **Wiring 500** — replace existing `exception<Throwable>` body with
   `call.respondHtml(serverErrorPage(), HttpStatusCode.InternalServerError)`. Preserve
   `logError(call, cause)`. The cause's `toString()` / `message` is **never** in the response
   body.
7. **Wiring banned** — change both `respond(HttpStatusCode.Forbidden, "You are banned…")`
   call sites in `RolePlugins.kt` (lines 65, 80) to
   `it.respondHtml(bannedPage(), HttpStatusCode.Forbidden)`.
8. **CSS:** new selectors live in `static/styles/components/error-page.css`, imported via
   `pageShell(stylesheets = listOf("/static/styles/components/error-page.css"))`. Tokens only
   — no hex.
9. **Integration tests** for each wired surface — see Acceptance Criteria. Each test
   installs `StatusPages` (and the `status(NotFound)` handler) inside its
   `testApplication { ... }` block, since the existing IT pattern does not include
   `configureStatusStaticRouter`.
10. **360px and 1280px screenshots** of all three pages attached to MCO-156.

### Out of scope

- **Maintenance page** — no maintenance-mode infrastructure exists. Dropped from scope.
- **Error-handling logic, auth flow, exception mapping** — templates and immediate
  response-body wiring only.
- **Inline HTMX 404 fragments** (e.g. `<p>Draft not found</p>` in `DraftHandlers.kt`) —
  HTMX swap targets, not full-page 404s.
- **`Component.kt` / `LeafComponent` / `createPage()` removal** — Feature 13 (MCO-142)
  territory.
- **Other 403 surfaces** (demo user, world permission, `/servers`) — need their own design
  decisions; some fire on HTMX requests where a full HTML page response is wrong.
- **Theming variants** — theme system being deleted in Feature 13.
- **HTMX-aware error responses** (`HX-Retarget` / `HX-Reswap`) — known existing failure
  mode (current plain-text body has the same problem); fix tracked separately.
- **Error-id / correlation-id on 500** — small follow-up; not blocking the core fix.
- **Consolidating `landing-brand-bar` and `error-brand-bar` into a shared `.brand-bar` class**
  — duplicate markup with scoped class names per Behaviour below; consolidation is a future
  cleanup if both surfaces stabilise.

## Behaviour

All three pages share the same chrome-less layout: `pageShell()` with a logo-only brand bar
at top (no nav, no breadcrumb, no toggle), centered `.surface` card with IBM Plex Mono
heading, Inter body copy, optional single primary action. Mobile and desktop use the same
vertical layout — no breakpoint variants.

The brand bar markup duplicates MCO-141 landing's `<header class="landing-brand-bar">`
pattern but uses scoped class names — `.error-brand-bar` / `.error-brand-bar__logo` —
defined in `error-page.css`. Markup duplication is intentional; consolidating into a shared
`.brand-bar` is out of scope.

### 404 — Not Found

Triggered by `StatusPages.status(HttpStatusCode.NotFound)` when no route matches. Renders
for both authenticated and unauthenticated users.

- Heading: `404 — Not Found`
- Body: `That page doesn't exist or has moved.`
- Primary CTA: `.btn--primary` `Back to worlds` linking to `/worlds`. (`/worlds` already
  redirects unauth users to OAuth sign-in, so a single generic CTA serves both audiences.)
- HTTP status: `404 Not Found`, `Content-Type: text/html`

### 500 — Server Error

Triggered by `exception<Throwable>` in `StatusPages`. Existing `logError(call, cause)` call
preserved.

- Heading: `500 — Something Broke`
- Body: `An unexpected error occurred. The error has been logged. Try again, or head back to
  your worlds.`
- Primary CTA: `.btn--primary` `Back to worlds` → `/worlds`
- HTTP status: `500 Internal Server Error`, `Content-Type: text/html`
- Response body **must not** contain the exception's class name or message
  (security-relevant — see Acceptance).

### Banned

Triggered by `BannedPlugin` for any authenticated request from a user with
`global_user_roles.role = 'banned'` (both the cache-hit and DB-lookup code paths in
`RolePlugins.kt`).

- Heading: `Account Suspended`
- Body: `Your account has been suspended from Seam. If you believe this is in error,
  contact support.`
- **No CTA** — copy only
- HTTP status: `403 Forbidden`, `Content-Type: text/html`

### Edge cases

- **HTMX requests hitting 404/500** — full status page HTML gets swapped into the original
  target. Existing failure mode (current plain-text body has the same issue); not fixed
  here. Documented out-of-scope.
- **Banned user on unauthenticated route** — `BannedPlugin` is installed on the protected
  route block; an unauth path won't trigger it. No change.
- **404 inside an authenticated section** (e.g. `/worlds/999/projects` where world 999
  doesn't exist) — most are returned by handlers as their own responses, not via the global
  404 handler. In-scope here is only the route-not-matched 404.

## Technical approach

- **Module:** all changes in `mc-web`. No domain, no pipeline, no migration.
- **New files:**
  - `presentation/templated/error/ErrorPageLayout.kt` —
    `errorPageLayout(title, body, ctaText?, ctaHref?)` returning `String` HTML
  - `presentation/templated/error/NotFoundPage.kt` — `fun notFoundPage(): String`
  - `presentation/templated/error/ServerErrorPage.kt` — `fun serverErrorPage(): String`
  - `presentation/templated/error/BannedPage.kt` — `fun bannedPage(): String`
  - Companion test files in `mc-web/src/test/kotlin/.../error/` and integration tests under
    the appropriate `*IT.kt` location
  - `static/styles/components/error-page.css`
- **Touched files:**
  - `presentation/plugins/Routing.kt` — replace exception body, add 404 handler
  - `presentation/plugins/RolePlugins.kt` — change two `respond(Forbidden, "...")` calls
- **HTML response helper:** use `app.mcorg.presentation.utils.respondHtml(html, statusCode)`
  from `presentation/utils/htmlResponseUtils.kt` for all three wirings. It already sets
  `Content-Type: text/html;charset=utf-8`. Do **not** use `respondText` and do **not** use
  `respondNotFound` / `respondBadRequest` from `htmxResponseUtils.kt` — those set
  `HX-ReTarget` / `HX-ReSwap` headers and are for HTMX swap fragments only.
- **Imports:** `import kotlinx.html.stream.createHTML` (per CLAUDE rules).
- **Auth:** unchanged — `BannedPlugin`'s detection logic and call sites stay identical;
  only the response body changes. This is **not** a restricted auth-plugin change requiring
  a human checkpoint.
- **`logError` reference in `Routing.kt`:** the existing exception handler calls
  `logError(call, cause)`. Verify import / definition before editing the body so the call
  site is preserved verbatim.
- **No new pipeline steps, no new routes, no new endpoints.**
- **Skills during impl:** `/docs-product`, `/docs-architecture`, `/docs-development`,
  `/docs-testing`. (`/docs-htmx` not needed — full-page responses, no HTMX swap concerns.)

### Testing approach

The existing `*IT.kt` files (e.g. `RolePluginsIT`, `GetLandingIT`) use
`testApplication { routing { ... } }` and do **not** call `configureStatusStaticRouter()`.
Each new IT must install the `StatusPages` block (or call
`configureStatusStaticRouter()`) inside its own `testApplication { ... }` setup before
defining the test route — otherwise Ktor's defaults swallow the assertion.

- **404 integration test:** install the production `StatusPages` block + custom
  `status(NotFound)` handler, GET `/this-route-does-not-exist`, assert 404 +
  `Content-Type: text/html` + body contains `404 — Not Found` and `Back to worlds`.
- **500 integration test:** install `StatusPages`, register a one-off route in the test app
  that throws, hit it, assert 500 + body contains `500 — Something Broke` + body **does
  not contain** the exception class name or message.
- **Banned integration test:** use existing `WithUser.createExtraUser("banned")` (which
  inserts the `global_user_roles` row via `addRole(userId, "banned")` — no new fixture
  needed). Hit any route in the protected block via the resulting authenticated client.
  Assert 403 + body contains `Account Suspended`. **Cache concern:** call
  `CacheManager.bannedUsers.invalidate(userId)` before the request to ensure the
  cache-miss / DB-lookup branch is exercised deterministically across test ordering. The
  cache-hit branch already has unit coverage in `CacheManagerTest`; add a one-line test
  comment noting which branch this IT covers.

## Sub-tasks

1. **`errorPageLayout` DSL helper + `error-page.css`** — chrome-less brand-bar shell,
   centered surface card. Defines `.error-brand-bar` / `.error-brand-bar__logo` markup
   (scoped duplicate of MCO-141 landing's brand bar). Lands first because sub-tasks 2/3/4
   import from this file.
2. **`notFoundPage()` template** + wire `StatusPages.status(NotFound)` handler in
   `Routing.kt` using `respondHtml` + integration test (installs `StatusPages` in the
   test app).
3. **`serverErrorPage()` template** + replace exception handler body in `Routing.kt` using
   `respondHtml` (preserve `logError`) + integration test (installs `StatusPages` in the
   test app, asserts no exception detail leak).
4. **`bannedPage()` template** + change both `respond(Forbidden, "…")` call sites in
   `RolePlugins.kt` to `respondHtml(bannedPage(), Forbidden)` + integration test using
   `WithUser.createExtraUser("banned")` + `CacheManager.bannedUsers.invalidate(userId)`
   pre-assertion.
5. **Screenshots** — 360px and 1280px for each of the three pages, attached to MCO-156.

## Acceptance criteria

- [ ] `notFoundPage()`, `serverErrorPage()`, `bannedPage()` exist in
      `presentation/templated/error/` and use `pageShell()` + `errorPageLayout`. None
      import `createPage()`, `Component`, `LeafComponent`, or any `templated/common/` class
      slated for F13 removal.
- [ ] `Routing.kt` `StatusPages` block has a `status(HttpStatusCode.NotFound)` handler
      responding with `respondHtml(notFoundPage(), NotFound)`.
- [ ] `Routing.kt` `StatusPages` block's `exception<Throwable>` handler responds with
      `respondHtml(serverErrorPage(), InternalServerError)`. Cause's `toString()`/`message`
      is **never** in the body. `logError(call, cause)` preserved.
- [ ] Both `respond(HttpStatusCode.Forbidden, "You are banned…")` sites in `RolePlugins.kt`
      (lines 65, 80) respond via `respondHtml(bannedPage(), Forbidden)`.
- [ ] Integration tests cover: 404 on unknown route, 500 on throwing test route, 403 on
      banned-user request — each installing `StatusPages` in its `testApplication { ... }`
      and asserting status, content-type, and key body strings.
- [ ] 500 integration test asserts response body does **not** contain the exception's class
      name or message.
- [ ] Banned IT calls `CacheManager.bannedUsers.invalidate(userId)` before the request.
- [ ] No inline `style=` in any of the three templates or the helper. All
      colors/spacing/font via tokens.
- [ ] `mvn clean compile` and `mvn test` pass from `webapp/`.
- [ ] 360px and 1280px screenshots of each page attached to MCO-156.
- [ ] Zero `createPage()` references in `templated/error/` (so F13 / MCO-142 can delete it
      without status-page entanglement).

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Banned response change breaks an existing test asserting plain text | LOW | Grep for `"You are banned"` literal before changing; update tests in sub-task 4. |
| 500 handler still leaks cause through some code path | LOW | Integration test asserts class name + message absent from body. |
| `errorPageLayout` brand bar diverges from MCO-141's landing brand bar | LOW | Markup duplication is intentional with scoped `.error-brand-bar` class names; consolidation deferred. |
| HTMX request hits 404/500, full page swaps into target | KNOWN, NOT FIXED | Documented out-of-scope. Existing behaviour today is the same. |
| `CacheManager.bannedUsers` populated by another test in the same run masks the new banned IT | LOW | Call `CacheManager.bannedUsers.invalidate(user.id)` before the request in the new IT. |

## Tech lead review

**Verdict:** Changes recommended (incorporated above).

**Key corrections applied:**

1. Use `app.mcorg.presentation.utils.respondHtml(html, statusCode)` for all three wirings —
   not `respondText`, and not the HTMX-flavoured `respondNotFound` / `respondBadRequest`.
2. Banned-user test fixture exists: `WithUser.createExtraUser("banned")` already inserts
   the role via `addRole(userId, "banned")`. No new helper needed.
3. Each integration test must install `StatusPages` inside its `testApplication { ... }` —
   the existing IT pattern does not call `configureStatusStaticRouter()`, so 500/404 tests
   would otherwise hit Ktor's defaults.
4. CSS class strategy explicit: `.error-brand-bar` / `.error-brand-bar__logo` scoped in
   `error-page.css`; consolidation with `landing-brand-bar` deferred.
5. Banned IT must invalidate `CacheManager.bannedUsers` before the request to deterministic-
   ally exercise the DB-lookup branch.
6. `/docs-htmx` dropped from skills list — full-page responses, no swap concerns.
7. Confirmed: this is **not** a restricted auth-plugin detection change — only the response
   body string flips. No human checkpoint required.
8. Sub-task ordering made explicit: helper + CSS lands before templates that import it.
