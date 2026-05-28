---
linear-issue: MCO-140
parent-epic: MCO-128
status: approved
type: feature
phase: 1
created: 2026-05-07
---

# Feature 11: World settings page rewrite

## Summary

Migrate `/worlds/:worldId/settings` from `createPage()` + Component classes to `pageShell()` + DSL +
docs-product design tokens. Same backing pipelines and HTMX contracts — new chrome and styling.
Tabs after this feature: **General**, **Members**, **Invitations** (split out from Members).
Statistics is dropped.

This rewrite also fixes a small auth bug: today the danger zone (Delete World) renders for any
ADMIN, but world deletion should be OWNER-only. The fix is server-side via a new
`WorldOwnerPlugin` installed on a nested DELETE route; UI hide is UX polish, not the security
boundary.

Layer 2 page rewrite. No domain changes, no migrations.

## User value

- **Technical player (owner / admin):** Settings stops being the one inconsistent surface in the
  app once the rest of the rewrite lands.
- **Owner managing members and invitations:** Splitting Invitations into its own tab gives the
  longest-living surface its own breathing room — today it sits wedged between the send-invite
  form and the members list, and accumulates cancelled / expired / declined entries over time.
- **Bug fix:** Non-owner admins no longer see (or can call) Delete World.
- **Blocker for Feature 13 cleanup.** Until settings migrates, `createPage()`, `tabsComponent`,
  and several legacy components (`avatar`, `chipComponent`, `actionButton`, `neutralButton`,
  `dangerButton`, legacy `dangerZone`) cannot be deleted from this surface.

## Scope

### In scope

1. Rewrite `worldSettingsPage()` (entry / tab framing) using `pageShell()` + `appHeader()` + DSL
   tab strip.
2. Rewrite `generalTab()` — name, version, description forms; danger zone gated to OWNER only.
3. **Split out** `invitationsTab()` from current `MembersTab` — send-invite form + invitations
   list with pill status filter (Pending / Accepted / Declined / Expired / Cancelled / All).
4. Rewrite `membersTab()` — members list + a one-line callout pointing to the Invitations tab.
   Send-invite form moves to Invitations.
5. Add `SettingsTab.Invitations` variant; add `?tab=invitations` parser branch in
   `handleGetWorldSettings`.
6. **Drop Statistics:** remove `SettingsTab.Statistics`, delete `StatisticsTab.kt`, remove the
   `"statistics"` parser branch (and the corresponding handler if it has one).
7. **Fix danger-zone gating, server-first.** Add `WorldOwnerPlugin` mirroring `WorldAdminPlugin`
   (same shape, `Role.OWNER` instead of `Role.ADMIN`). Install it on a *nested* `route` inside
   `/settings` containing only the `delete { call.handleDeleteWorld() }` handler — not at the
   `/settings` root, which would lock admins out of viewing settings. **Pause for explicit
   human ack** before committing the new plugin file (auth plugin changes are flagged in
   CLAUDE.md). Then thread `currentUserRole: Role` into `SettingsTab.General`, resolved in
   `handleGetGeneralTabData` via cached `ValidateWorldMemberRole(user, Role.OWNER, worldId)`,
   and gate the `dangerZone` block on `currentUserRole == Role.OWNER`. UI hide is UX polish;
   `WorldOwnerPlugin` is the security boundary.
8. Replace legacy `tabsComponent` for both layers (page tabs and inner invitation status pills)
   with DSL primitives.
9. Replace legacy components (`actionButton`, `neutralButton`, `dangerButton`, `chipComponent`,
   `avatar`, `dangerZone`, `tabsComponent`) with DSL equivalents:
   - Buttons → existing DSL `primaryButton` / `secondaryButton` / `dangerButton` / `ghostButton`
     `[ASSUMPTION: names match prior Layer 2 features; verify against `templated/dsl/` at impl time]`
   - Chips → DSL `badge` `[ASSUMPTION: helper exists per docs-product; if not, add `templated/dsl/Badge.kt`]`
   - Avatar → DSL primitive `[GAP: confirm whether `templated/dsl/` has an avatar helper; if not, port from `templated/common/avatar/`]`
   - Danger zone → port to DSL exactly as Feature 12 plans. **First feature to land does the
     port; other consumes.** `[SCOPE: duplicated work between F11 and F12 — coordinate via Linear comments]`
   - Tab strip → DSL helper `[GAP: confirm presence; sized to support both page tabs and pill-variant invitation status filter]`
   - DSL ports happen **alongside the first consumer**, not as a standalone audit sub-task. A
     standalone "DSL audit" commit would not compile until consumers wired in.
10. Preserve all existing HTMX endpoint URLs, swap targets, and response shapes — except two
    documented contract changes:
    - Tab-swap target ID rename: `.world-settings-content` (class) → `#world-settings-content`
      (id). Atomic commit; grep for the old class first.
    - Invitation list target ID: existing `<ul class="invitation-list">` becomes
      `<ul id="invitation-list">` (the class can stay as well if any CSS depends on it; the swap
      target is the id). All `hxTarget(".invitation-list")` call sites switch to
      `hxTarget("#invitation-list")`.
11. Keep invitation URLs at `/settings/members/invitations*` (the split is UI-only). No pipeline
    URL renaming.
12. Pill status filter URLs **stay `?status=…` only** (today's behaviour). Extend the parser in
    `GetWorldSettingsPipeline` to also accept `tab == "invitations" && status != null &&
    isHtmxRequest` → fragment, alongside the existing `tab == null && status != null` branch.
    Additive; no existing flow changes.
13. Mobile-first per docs-product (1080px max, 4px grid, dark-only).
14. Integration tests preserved / updated; new tests added for `WorldOwnerPlugin` enforcement
    on `DELETE /settings`, danger-zone visibility per role, and Invitations tab routing.

### Out of scope

- `Component.kt` / `LeafComponent` removal — Feature 13.
- Profile editing, member display name editing, role naming changes.
- Bulk member ops, sort, search inside members list.
- Statistics data work (queries, schema). Statistics is dropped from this UI; any future
  statistics feature is its own scope.
- Notification of removed members / cancelled invitations — preserved as-is.
- Renaming invitation URLs from `/settings/members/invitations*` to `/settings/invitations*`.
- Implementing SuperAdmin-driven world deletion (currently a stub at
  `AdminHandler.kt:36–46`). When that surface is built, it must use a *different* route under
  `/admin`, gated by `AdminPlugin`'s `isSuperAdmin` check, and bypass `WorldOwnerPlugin`.

## Behaviour

### Page shell (all tabs)

- `pageShell()` + `appHeader()`. Breadcrumb: `Worlds > [World Name] > Settings` (DSL breadcrumb
  replaces `BreadcrumbBuilder.buildForWorldSettings`).
- Page heading `World Settings` (IBM Plex Mono, `--text-heading`). Subhead `Manage your world
  settings, members, and invitations` (Inter, `--text-secondary`).
- Tab strip below heading: `General` | `Members` | `Invitations`. Active tab styled per
  docs-product.
- No inline `style=`. All spacing via design-token utility classes.

### Tab navigation (HTMX contract)

Today: top-level tabs HTMX-swap. `tabsComponent(hxTarget = ".world-settings-content", …)` fires
`GET /worlds/{worldId}/settings?tab={id}` with HX-Request, and `handleGetWorldSettings` returns
either the full page or the `<div class="world-settings-content">…</div>` fragment.

After:
- Tab links target `#world-settings-content` (renamed from class to ID for clarity; the inner
  Invitations tab's pill filter targets `#invitation-list` so the two swap targets are
  namespaced and don't collide).
- Tab swap response is `<div id="world-settings-content">…</div>` containing the new tab body.
- Non-HTMX requests render full page with requested tab pre-selected.
- Query param: `?tab=general|members|invitations`. Invalid → fallback to General. `"statistics"`
  no longer parsed.

Mobile: tab strip wraps to two rows if needed. Desktop: single row.

### General tab

Section heading `General Settings` + one-line description.

Three forms, each in a `.surface` card on desktop, full-bleed on mobile `[ASSUMPTION: surface
card pattern matches docs-product; verify with /docs-product]`:

1. **World Name** — text input (3–100 chars, required). `PATCH /worlds/{worldId}/settings/name`,
   debounce 500ms on input change + submit. Validation errors → `#validation-error-name`.
   Success notice → `#alert-container` via `hx-swap="afterbegin"`.
2. **World Description** — textarea (max 500 chars). `PATCH /settings/description`, same shape.
3. **Game Version** — `<select>` from `GetSupportedVersionsStep`. `PATCH /settings/version` on
   `change`.

Required `*` indicator preserved. Field labels use docs-product label class.

**Danger zone (OWNER only):** rendered only when `currentUserRole == Role.OWNER`. "Delete World"
→ typed-confirm modal (must type world name) → `DELETE /worlds/{worldId}/settings`. Reuses
ported `dangerZone` DSL + `hxDeleteWithConfirm`. After delete, redirect to `/worlds`.

For non-owner admins: danger zone simply isn't rendered. **Server-side defence:**
`WorldOwnerPlugin` rejects non-OWNER `DELETE /settings` with 403 — covers stale tabs and
hand-crafted requests.

Mobile: forms stack full width. Desktop: single column max-width 720px `[ASSUMPTION]`.

### Members tab

Two sub-sections, top to bottom:

1. **Members list**
   - Heading `World Members` + helper paragraph (preserve copy explaining roles).
   - Each member row: avatar, display name, role text, joined date.
   - For lower-role-than-current (and not Owner): role select + "Remove member" button.
     - Role select: `PATCH /worlds/{worldId}/settings/members/{memberId}/role` on change,
       target `#member-{id}-role-display` swap `innerHTML` (preserve current contract).
     - Remove: `hxDeleteWithConfirm` → `DELETE /worlds/{worldId}/settings/members/{memberId}`,
       target `#member-{id}` swap `delete`.
2. **Pointer to Invitations tab** — short callout `Need to invite someone or review pending
   invitations? Open the Invitations tab.` with a link `?tab=invitations`. `[ASSUMPTION: a
   one-line callout is enough; alternative is no pointer at all and trusting users to find the
   tab. Recommend the callout for discoverability.]`

Mobile: rows collapse — avatar + name + role on one line, actions drop to second line. Desktop:
single row, actions right-aligned.

### Invitations tab (NEW — split from Members)

Two sub-sections:

1. **Send invitation form**
   - Heading `Send invitation` + description.
   - Inputs: Minecraft username (text, required, placeholder `Alex`), Role (select: Member /
     Admin only — Banned and Owner excluded). Submit: primary.
   - HTMX: `POST /worlds/{worldId}/settings/members/invitations`. Target `#invitation-list`,
     swap `afterbegin`. On 2xx, reset form + clear validation messages (preserve current
     `hx-on::after-request`).
   - Validation errors via `hx-target-error=".validation-error-message"`.

2. **Invitations list with status filter**
   - Pill sub-tabs: Pending / Accepted / Declined / Expired / Cancelled / All, with counts
     (`Pending (3)`).
   - Sub-tabs target `#invitation-list` and pass `?status={filter}` (no `&tab=` param — keep
     today's contract). Server returns filtered `<ul id="invitation-list" class="invitation-list">…</ul>`
     via `handleGetInvitationListFragment`. Parser in `GetWorldSettingsPipeline.kt:42` extended
     to also match `tab == "invitations" && status != null && isHtmxRequest` → fragment
     (additive).
   - Each invite row: avatar, target username, role badge, status badge, sent-at, expires-at.
     Pending shows "Cancel" button → `hxDeleteWithConfirm` against
     `/settings/members/invitations/{inviteId}`, target `#invite-{id}`, swap `delete`.
   - Empty list: `No invitations found with this status.`

Mobile: rows collapse same as members rows. Desktop: standard data row.

## Technical approach

### Files touched

**Templates (rewritten / added / deleted):**
- `presentation/templated/settings/SettingsPage.kt` — drop Statistics from sealed interface, add
  Invitations variant, swap to `pageShell()` + DSL tabs
- `presentation/templated/settings/GeneralTab.kt` — DSL primitives, gated danger zone, accept
  `currentUserRole`
- `presentation/templated/settings/MembersTab.kt` — members list only + Invitations callout
- `presentation/templated/settings/InvitationsTab.kt` — **new** — send-invite form + invitations
  list + pill filter
- `presentation/templated/settings/StatisticsTab.kt` — **deleted**

**Pipelines (light edits, no logic changes):**
- `pipeline/world/settings/GetWorldSettingsPipeline.kt` — drop `"statistics"` parser branch, add
  `"invitations"` branch, extend the fragment-routing branch to accept `tab == "invitations" &&
  status != null && isHtmxRequest`, update outer wrapper to `id="world-settings-content"`,
  thread `currentUserRole` into `SettingsTab.General` via cached `ValidateWorldMemberRole`
- `pipeline/world/settings/invitations/GetInvitationListFragmentPipeline.kt` — add `id =
  "invitation-list"` to the existing `ul` (line ~20)
- `pipeline/world/DeleteWorldPipeline.kt` — add a TODO comment noting the `HX-Current-URL
  contains "/admin"` branch is currently unreachable (`AdminHandler.kt:36–46` is a stub); leave
  the branch intact for future SuperAdmin world-deletion flow

**Auth plugins:**
- `presentation/plugins/RolePlugins.kt` — add `WorldOwnerPlugin` mirroring `WorldAdminPlugin`,
  using `ValidateWorldMemberRole(user, Role.OWNER, worldId)` and the same 403 response shape

**Routes:**
- `WorldHandler.kt:285–300` — wrap the existing `delete { call.handleDeleteWorld() }` in a
  nested `route("") { install(WorldOwnerPlugin); delete { … } }` (or equivalent Ktor shape that
  scopes the plugin to the DELETE only)

**DSL (added or extended in `templated/dsl/`):**
- `Tabs.kt` — top-level tab strip + pill variant `[GAP: confirm not present]`
- `Avatar.kt` — small/medium primitive `[GAP: confirm; port from `templated/common/avatar/` if absent]`
- `Badge.kt` — text badge for role + invite status `[GAP: confirm presence]`
- `DangerZone.kt` — port from `templated/common/dangerzone/DangerZone.kt` (coordinate with F12)
- Form helpers (label + input + validation paragraph) `[GAP: confirm whether `formField` DSL helper exists]`

### Routes / URLs

No URL changes. All ten HTMX endpoints unchanged. Invitations tab reuses
`/settings/members/invitations*` URLs (UI-only split).

| Method | Path                                                          | Handler                              | Plugin             |
|--------|---------------------------------------------------------------|--------------------------------------|--------------------|
| GET    | `/worlds/{worldId}/settings`                                  | `handleGetWorldSettings`             | `WorldAdminPlugin` |
| PATCH  | `/worlds/{worldId}/settings/name`                             | `handleUpdateWorldName`              | `WorldAdminPlugin` |
| PATCH  | `/worlds/{worldId}/settings/description`                      | `handleUpdateWorldDescription`       | `WorldAdminPlugin` |
| PATCH  | `/worlds/{worldId}/settings/version`                          | `handleUpdateWorldVersion`           | `WorldAdminPlugin` |
| DELETE | `/worlds/{worldId}/settings`                                  | `handleDeleteWorld`                  | `WorldAdminPlugin` + **`WorldOwnerPlugin`** |
| GET    | `/worlds/{worldId}/settings/members/invitations`              | `handleGetInvitationListFragment`    | `WorldAdminPlugin` |
| POST   | `/worlds/{worldId}/settings/members/invitations`              | `handleCreateInvitation`             | `WorldAdminPlugin` |
| DELETE | `/worlds/{worldId}/settings/members/invitations/{inviteId}`   | `handleCancelInvitation`             | `WorldAdminPlugin` |
| PATCH  | `/worlds/{worldId}/settings/members/{memberId}/role`          | `handleUpdateWorldMemberRole`        | `WorldAdminPlugin` |
| DELETE | `/worlds/{worldId}/settings/members/{memberId}`               | `handleRemoveWorldMember`            | `WorldAdminPlugin` |

### Auth / plugins

- `WorldAdminPlugin` (requires `Role.ADMIN`+) stays installed on `/settings` block.
- **New `WorldOwnerPlugin`** installed on the nested DELETE route only. Same shape as
  `WorldAdminPlugin`, just `Role.OWNER`. Pause for human ack before committing the plugin file.
- Tests must cover MEMBER → 403 from `WorldAdminPlugin` at the route boundary (not at render),
  ADMIN → can view General but DELETE returns 403, OWNER → full access.

### Database

No changes.

### Skills during impl

`/docs-product`, `/docs-architecture`, `/docs-htmx`, `/docs-development`, `/docs-testing`.

## Sub-tasks

1. **Add `WorldOwnerPlugin` + nested DELETE route + integration test.** Pure auth fix, mergeable
   independently. Tests: OWNER 200, ADMIN 403, MEMBER 403 (from outer plugin), non-member 403.
   **Pause for explicit human ack on the new plugin file before committing.**
2. **Drop `SettingsTab.Statistics`.** Delete `StatisticsTab.kt`, remove the sealed variant,
   remove the `"statistics"` parser branch and any orphaned handler. Independent commit.
3. **Add `SettingsTab.Invitations` + parser branches.** New variant in the sealed interface,
   `?tab=invitations` parser, extend fragment-routing parser to accept `tab == "invitations" &&
   status != null && isHtmxRequest`. Still rendered with old chrome — compiles, tests pass.
4. **Page shell rewrite + class→ID rename.** `createPage()` → `pageShell()` + `appHeader()`,
   tabs swap to DSL tab strip, `.world-settings-content` → `#world-settings-content`. Update
   `GetWorldSettingsPipeline` wrapper to `id="world-settings-content"`. Grep
   `world-settings-content` and update all references atomically.
5. **`id="invitation-list"` wrapper + target switch.** Add `id = "invitation-list"` to existing
   `<ul>` in `GetInvitationListFragmentPipeline.kt:20` and `worldInvitations` in
   `MembersTab.kt:122–123`. Switch `hxTarget(".invitation-list")` → `hxTarget("#invitation-list")`
   at all sites (notably `MembersTab.kt:42`). Atomic commit.
6. **General tab DSL rewrite + danger-zone OWNER gating.** Thread `currentUserRole: Role` into
   `SettingsTab.General` via cached `ValidateWorldMemberRole(user, Role.OWNER, worldId)` in
   `handleGetGeneralTabData`. Render `dangerZone { … }` only when `currentUserRole ==
   Role.OWNER`. Port DSL helpers as needed for this commit.
7. **Members tab DSL rewrite.** Members-list-only + one-line callout pointing to Invitations.
8. **New Invitations tab DSL rewrite.** Send-invite form + invitations list with pill status
   filter. Pills target `#invitation-list` and pass `?status={filter}` only.
9. **Manual visual QA + screenshots.** 360px and 1280px screenshots of all three tabs attached
   to MCO-140.

`[SIZE: 9 sub-tasks. Each compiles and tests pass; sub-task 1 is independently mergeable.]`

## Acceptance criteria

- [ ] `WorldOwnerPlugin` exists in `RolePlugins.kt`, mirrors `WorldAdminPlugin` shape, installed
      on a nested route scoped to `DELETE /settings` only.
- [ ] Integration test: `DELETE /worlds/{worldId}/settings` returns 200 for OWNER, 403 for ADMIN
      (non-owner), 403 for MEMBER, 403 for non-member.
- [ ] Settings templates use `pageShell()` + `appHeader()`. No `createPage()` import in
      `SettingsPage.kt`, `GeneralTab.kt`, `MembersTab.kt`, `InvitationsTab.kt`.
- [ ] `StatisticsTab.kt` deleted. `SettingsTab.Statistics` removed. `"statistics"` parser branch
      removed.
- [ ] `SettingsTab.Invitations` exists; `?tab=invitations` lands on the Invitations tab (full
      page) and HTMX-swaps the fragment correctly.
- [ ] Pill status filter URLs are `?status=…` only (no `&tab=` param). Parser in
      `GetWorldSettingsPipeline.kt` accepts `tab == "invitations" && status != null &&
      isHtmxRequest` → fragment.
- [ ] No `templated/common/...` legacy component imports in the four settings templates.
- [ ] All ten HTMX endpoints return same response shape; the two documented contract changes
      (`#world-settings-content` and `#invitation-list`) are reflected in both server markup and
      client `hx-target` attributes.
- [ ] Danger zone visible only when `currentUserRole == Role.OWNER`. Non-owner admin sees no
      Delete World button.
- [ ] Tab swap (General ↔ Members ↔ Invitations) via HTMX without full reload. Direct nav to
      `?tab=members` and `?tab=invitations` lands correctly. Invalid `?tab=` falls back to
      General.
- [ ] Invitation status filter swaps invitation list via HTMX without re-rendering rest of the
      Invitations tab.
- [ ] No inline `style=` in any settings template.
- [ ] All colors / spacing / typography reference docs-product CSS variables.
- [ ] 360px and 1280px screenshots of all three tabs attached to MCO-140.
- [ ] `mvn clean compile` and `mvn test` pass.

## Risks

| Risk                                                                    | Severity | Mitigation                                                                                                                                              |
|-------------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Class→ID rename leaks (legacy CSS / JS / tests reference `.world-settings-content` or `.invitation-list`) | MEDIUM   | `grep -r "world-settings-content"` and `grep -r "invitation-list"` after each rename; update all references in one atomic commit per rename. Keep CSS class on `<ul>` if needed; the swap target is the id. |
| `dangerZone` port collides with F12                                     | MEDIUM   | Coordinate via Linear comments — first to land does the port; the other consumes.                                                                       |
| Invitations split increases scope beyond migrate-only                   | MEDIUM   | Acknowledged. Sub-task 8 is its own commit; the split has product value (cleaner mental model on a busy tab).                                           |
| Avatar / badge ports duplicate F6b or F7a work                          | LOW      | Audit `templated/dsl/` first; port only what's actually missing.                                                                                        |
| Fragment-routing parser extension misses an edge case                   | LOW      | Add unit test for `GetWorldSettingsPipeline` parser covering: `tab=invitations`, `tab=invitations&status=pending`, `status=pending` (no tab), `status=…&tab=invitations` HTMX vs non-HTMX. |
| New `WorldOwnerPlugin` accidentally installed at `/settings` root, locking admins out | MEDIUM   | Code review; integration test asserting ADMIN can `GET /settings` and `PATCH /settings/name`. Plugin must be on the *nested* DELETE route only.         |
| Existing integration tests reference tab classes / selectors            | LOW      | Run `mvn test -pl mc-web` after sub-tasks 4 and 5; update test expectations alongside markup changes.                                                   |

## Out of scope / deferred

| Item                                                              | Why                                              | Where it belongs                                |
|-------------------------------------------------------------------|--------------------------------------------------|-------------------------------------------------|
| Statistics page (any future statistics)                           | Dropped from this UI; needs proper scoping       | Future "World Statistics" feature, separate Linear issue |
| `Component.kt` / `LeafComponent` removal                          | Feature 13's job                                 | Feature 13                                      |
| Profile editing, member display name editing                      | Not a current feature                            | Future profile feature                          |
| Bulk member ops, search, sort                                     | Not a current feature                            | Future feature                                  |
| Audit log of role changes / invite cancellations                  | Not a current feature                            | Future audit-log feature                        |
| Renaming invitation URLs from `/settings/members/invitations*`    | Pipeline-touching, no user value                 | Out of scope; revisit only if it causes confusion |
| SuperAdmin-driven world deletion from `/admin`                    | Currently a stub at `AdminHandler.kt:36–46`      | Future admin feature; will use a different route under `/admin` gated by `AdminPlugin`'s `isSuperAdmin` check, bypassing `WorldOwnerPlugin` |
| `/docs-frontend` skill content for settings patterns              | Feature 14                                       | Feature 14                                      |

## Tech lead review

**Verdict:** Changes recommended (incorporated above)

**Key corrections applied:**

1. Auth approach (a) chosen — introduce `WorldOwnerPlugin` mirroring `WorldAdminPlugin`,
   installed on a nested DELETE route only. Pause for explicit human ack before committing the
   new plugin file (auth plugin changes are flagged in CLAUDE.md).
2. Pill status filter URLs stay `?status=…` only — adding `&tab=invitations` would have broken
   the existing `tab == null && status != null` parser branch. Parser is extended additively
   instead.
3. Admin-surface DELETE branch in `handleDeleteWorld` is dead code today (`AdminHandler.kt:36–46`
   is a stub) — original Risk #4 resolved; leave the branch with a TODO for future SuperAdmin
   world-deletion.
4. `currentUserRole: Role` threaded into `SettingsTab.General` via cached
   `ValidateWorldMemberRole(user, Role.OWNER, worldId)` in `handleGetGeneralTabData`. Server
   gating (`WorldOwnerPlugin`) is the security boundary; UI hide is UX polish.
5. `id="invitation-list"` added to existing `<ul>`, not a new wrapper. All
   `hxTarget(".invitation-list")` call sites switch to `hxTarget("#invitation-list")`.
6. DSL ports happen alongside their first consumer — no standalone "DSL audit" commit (would
   not compile until consumers wired in).
7. Sub-task ordering rewritten: auth fix is sub-task 1, independently mergeable. Each
   subsequent sub-task compiles and passes tests.
8. Test scope clarified: MEMBER gets 403 from `WorldAdminPlugin` at the route boundary, not at
   render. OWNER vs ADMIN tests cover General render with danger zone gated.
