---
linear-issue: MCO-155
status: approved
phase: 1
created: 2026-04-09
parent-epic: MCO-128
epic-feature: 6b
---

# Pending invitations on the world list page

## Summary

Surface pending world invitations on `/worlds` (the user's front page) so an invited
user can see and act on invitations without leaving their main landing surface.
Accept and decline happen inline via HTMX with no full page reload. Closes Feature 6b
of the Frontend Rewrite epic (MCO-128). Today the rewritten `worldListPage` never
wires in invitations and the legacy `home/PendingInvitesView.kt` template is dead
code with no callers — a user invited to a world has no way to accept from the UI.

## User value

- **Technical player** (primary): invited by collaborators to shared worlds.
- **Casual player** (secondary): invited by a friend; needs obvious low-friction path.
- **Worker** (tertiary): invitation is their first touchpoint with the product.

What changes: an invited user lands on `/worlds` and immediately sees a
"Pending invitations" section above the world list. One click to accept (row
disappears, world appears in the list). One click to decline with confirmation.

If we don't build it: invitations are functionally invisible in the rewritten UI.
Hard blocker for multi-user scenarios and a regression from pre-rewrite.

## Scope

**In scope:**

- Pending invitations section on `/worlds`, rendered above the worlds toolbar and
  world list, using `/docs-product` tokens and the new DSL.
- Inline accept/decline via existing `PATCH /invites/{inviteId}/accept` and
  `PATCH /invites/{inviteId}/decline` endpoints.
- Relative expiry text in the UI ("in 7 days", "in 2 hours"). See Technical
  approach for a note on the synthetic expiry value.
- HTMX-driven row removal on decline. On accept: row removed plus world list
  refresh via `HX-Trigger: worldListChanged` header + listener on
  `#world-card-list` that re-fetches from `GET /worlds/search`.
- "Last invitation" case: OOB swap of `#pending-invitations-section` to empty so
  the section disappears entirely.
- Empty state: section absent from the DOM when no pending invitations exist.
- Mobile-first layout with a new `invitationRow` DSL component.
- Deletion of the legacy dead code under `presentation/templated/home/`:
  `PendingInvitesView.kt`, `HomePage.kt`, `WorldsView.kt`, `WorldView.kt`,
  `CreateWorld.kt`. Plus the `@import "pages/homepage.css";` line at
  `styles.css:40` and `static/styles/pages/homepage.css`.

**Out of scope:**

- Dedicated `/invitations` page
- Notification badge / dropdown in app header (deferred to Feature 12 or later)
- Toast / snackbar primitive — this task reuses the existing `notice notice--*`
  pattern. A real toast primitive can come from the notifications epic.
- Historical view of declined/expired invitations
- Real-time invitation push
- Creating / sending invitations (already in world settings → members tab)
- Sorting / filtering / pagination / bulk actions
- Changes to `Invite`, `InviteStatus`, or the invitation DB schema
- Adding a real `expires_at` column (see Technical approach note)

## Behaviour

**Placement:** Section inside `worldsContent` in `WorldListPage.kt`, rendered
above the worlds toolbar and world list. When invitations exist they demand
action; when they don't, the section is absent and the page looks unchanged.

**Happy path (user has invitations):**

1. User navigates to `/worlds`.
2. Section renders above the world list with heading ("Pending invitations") and a
   subtle subheading ("You've been invited to join these worlds").
3. Each invitation row shows:
   - World name (primary text)
   - Secondary line: "Invited by {fromUsername} • Role: {role} • Expires {relative}"
     where relative is formatted from the stored `expiresAt` — e.g. "in 7 days",
     "in 2 hours".
   - `Accept` (primary button) and `Decline` (ghost/neutral button).
4. Below the section, normal worlds toolbar and world list render.

**Accept flow:**

1. User clicks `Accept`.
2. `hx-confirm="Accept invitation to join {worldName}?"` fires.
3. On confirm: `PATCH /invites/{inviteId}/accept`.
4. Server responds with an empty fragment replacing the invitation row, plus
   `HX-Trigger: worldListChanged` header.
5. Listener on `#world-card-list` (`hx-trigger="worldListChanged from:body"`,
   `hx-get="/worlds/search"`) re-fetches the world list so the newly joined world
   appears.
6. If this was the last pending invitation, the server response also includes an
   `hx-swap-oob="outerHTML:#pending-invitations-section"` wrapping an empty span.
7. A `notice notice--success` fragment confirms ("Joined {worldName}"), consistent
   with the existing handler pattern.

**Decline flow:**

1. User clicks `Decline`.
2. `hx-confirm="Decline invitation to {worldName}?"` fires.
3. On confirm: `PATCH /invites/{inviteId}/decline`.
4. Server responds with an empty fragment swapping out the row. No world list
   refresh needed.
5. If last invitation, same OOB section removal.
6. `notice notice--success` confirms.

**Empty state:** Section entirely absent from the DOM. No placeholder copy.

**Edge cases:**

- **Expired between load and click**: pipeline fails; response renders an inline
  `notice notice--error` on the row and swaps the row out.
- **Already a member (race)**: pipeline fails; same error-notice + row-swap
  pattern.
- **DB / pipeline error**: standard `handlePipeline` error rendering. Row stays,
  error notice appears.
- **Mobile (<768px)**: `.invitation-row` content column stacks
  (`flex-direction: column`), metadata wraps below the world name,
  `.invitation-row__actions` flips to `flex-direction: column` with `flex: 1`
  buttons so Accept/Decline fill the full width.

## Technical approach

**Modules:** `mc-web` only. No `mc-domain`, `mc-pipeline`, `mc-engine`, `mc-data`
changes. No DB migrations.

**Reused existing pieces:**

- `AcceptInvitationPipeline` / `handleAcceptInvitation` at
  `PATCH /invites/{inviteId}/accept`. Response HTML changes only.
- `DeclineInvitationPipeline` / `handleDeclineInvitation` at
  `PATCH /invites/{inviteId}/decline`. Same.
- `GetUserInvitationsStep` — already filters to PENDING + joins world + user.
- `handleSearchWorlds` at `GET /worlds/search` — already returns an
  `#world-card-list` fragment with `outerHTML` swap semantics. Used as the
  invalidation target for the `worldListChanged` trigger. Verify during
  implementation that it handles an empty `query` param by returning all worlds
  for the current user (it should — the search box already debounces on empty
  input).
- `Invite` / `InviteStatus` domain models unchanged.

**Synthetic expiry — important note:** `InviteExtractors.kt:20` computes
`expiresAt = createdAt + 7 days`. There is no `expires_at` column on the invite
row; the DB only stores `created_at`. The relative-expiry display is faithful to
the domain value but will always show "in ~7 days" for freshly-created invites,
rounding down as real time passes. We accept this — a real `expires_at` column
is out of scope and would require a migration not worth the complexity for
display purposes. Document this behavior in the PR description and move on.

**New code:**

1. **`WorldHandler.handleGetHome()`** (lines 331–342 of `WorldHandler.kt`) —
   inside the existing handler body, run `GetUserInvitationsStep.run(user.id)`
   and pass the result into `worldListPage(…)`.

2. **`worldListPage(…)` in `presentation/templated/dsl/pages/WorldListPage.kt`** —
   new parameter `pendingInvitations: List<Invite>`. Render
   `pendingInvitationsSection(pendingInvitations)` at the top of `worldsContent`,
   above the toolbar. Also add the invalidation listener to `#world-card-list`:

   ```kotlin
   attributes["hx-get"] = "/worlds/search"
   attributes["hx-trigger"] = "worldListChanged from:body"
   attributes["hx-swap"] = "outerHTML"
   ```

   Also verify the existing notice target id in `worldListPage` (the one the
   accept/decline handlers already OOB-swap into). If absent, add a
   `<div id="notice-container">` (or whatever the existing convention is across
   rewritten pages) before wiring the OOB target.

3. **New DSL component file:
   `presentation/templated/dsl/components/PendingInvitationsSection.kt`**
   - `fun FlowContent.pendingInvitationsSection(invitations: List<Invite>)` —
     renders nothing if empty, otherwise a
     `<section id="pending-invitations-section">` containing a heading and one
     `invitationRow` per invitation.
   - `fun FlowContent.invitationRow(invitation: Invite)` — new row primitive
     with DOM id `#pending-invitation-{inviteId}`. Layout: flex row desktop
     (primary/secondary text left, action buttons right); at `<768px`, stacks
     both the content column and the actions column to `flex-direction: column`
     so buttons become full-width.
   - Accept/Decline buttons must use `hx-target="#pending-invitation-{inviteId}"`
     and `hx-swap="outerHTML"` so the empty fragment replaces the row.

4. **Relative-expiry helper** — small Kotlin utility in
   `presentation/templated/utils/` (or alongside the component), using
   `java.time.Duration` to format `(expiresAt - now)` into "in N days" /
   "in N hours" / "in N minutes" / "tomorrow" / "today".

5. **Update `handleAcceptInvitation`** (in `AcceptInvitationPipeline.kt`):
   - Response replaces the invitation row with an empty fragment instead of the
     current `notice notice--success` replacing an empty div.
   - Add `HX-Trigger: worldListChanged` header via
     `call.response.header("HX-Trigger", "worldListChanged")`. Verify
     `handlePipeline` permits header mutation at the success-branch callsite;
     if it doesn't, use `respondHtml` directly.
   - If post-accept remaining invitations == 0, include an
     `hx-swap-oob="outerHTML:#pending-invitations-section"` wrapping an empty
     span in the response.
   - Also emit a `notice notice--success` for "Joined {worldName}" via OOB swap
     into the existing notice target.
   - Response HTML ordering: primary fragment first (empty row), then OOB
     fragments (`#pending-invitations-section` removal when last, notice).

6. **Update `handleDeclineInvitation`** (in `DeclineInvitationPipeline.kt`):
   - Simpler version of the same: empty row fragment + optional OOB section
     removal + `notice notice--success` for "Declined invitation to {worldName}".
   - No `HX-Trigger` (world list doesn't change).

7. **Delete legacy dead code:**
   - `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/home/PendingInvitesView.kt`
   - `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/home/HomePage.kt`
   - `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/home/WorldsView.kt`
   - `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/home/WorldView.kt`
   - `webapp/mc-web/src/main/kotlin/app/mcorg/presentation/templated/home/CreateWorld.kt`
   - `webapp/mc-web/src/main/resources/static/styles/pages/homepage.css`
   - Line 40 of `webapp/mc-web/src/main/resources/static/styles/styles.css`:
     remove the `@import "pages/homepage.css";` statement.

8. **New CSS: `static/styles/components/pending-invitations.css`** — component
   styles, imported from `styles.css`. Desktop uses flex with content column
   left, actions column right-aligned via `margin-left: auto`. At
   `max-width: 767px`, content column flips to stacked text, actions column
   flips to `flex-direction: column` with `flex: 1` buttons, 12px gap, full
   width.

**Skills to load during implementation:** `/docs-product`, `/docs-htmx`,
`/docs-development`, `/docs-testing`.

## Sub-tasks

1. **Extend `handleGetHome`** to fetch pending invitations and pass them into
   `worldListPage(…)`.
2. **Build `invitationRow` + `pendingInvitationsSection` DSL components +
   relative-expiry helper, and wire the section into `worldListPage`.** Includes
   the `#world-card-list` invalidation listener setup and verifying/adding the
   notice target container.
3. **Update `handleAcceptInvitation` and `handleDeclineInvitation`** — empty row
   fragments, conditional OOB section removal, success notices, `HX-Trigger` on
   accept.
4. **New CSS** `components/pending-invitations.css` including mobile stacking.
5. **Delete legacy dead code** under `templated/home/`, plus the `styles.css`
   import line and `homepage.css`. Verify `mvn clean compile` still passes.
6. **Integration tests** covering the acceptance criteria scenarios. Ship with a
   UX review (screenshots desktop + mobile) as the merge gate.

## Acceptance criteria

- [ ] User with 0 pending invitations loading `/worlds` sees no invitations
      section in the DOM.
- [ ] User with 1+ pending invitations sees the section above the world list with
      world name, inviter, role, and relative expiry on each row.
- [ ] Relative expiry: "in N days" / "in N hours" / "in N minutes" / "tomorrow"
      / "today", based on the synthetic `expiresAt`.
- [ ] Accepting an invitation:
      - removes the row from the DOM
      - refreshes `#world-card-list` via the `worldListChanged` HX trigger
      - shows a `notice notice--success` confirmation
      - adds the user as a member of the world (pipeline side effect)
- [ ] Declining an invitation:
      - prompts for confirmation
      - removes the row from the DOM on confirm
      - shows a `notice notice--success` confirmation
      - updates the invitation status to `DECLINED`
      - leaves the world list unchanged
- [ ] Accepting or declining the last pending invitation removes the entire
      `#pending-invitations-section` wrapper from the DOM.
- [ ] Expired-between-load-and-click invitations surface as inline
      `notice notice--error` and swap the row out.
- [ ] Already-a-member race surfaces the same way.
- [ ] Mobile (<768px): rows stack vertically, metadata wraps, Accept/Decline
      buttons stretch to full-width stacked.
- [ ] Integration tests cover:
      (a) `GET /worlds` with 2 pending invitations renders both rows inside the
          section
      (b) `PATCH /invites/{id}/accept` returns an empty row fragment +
          `HX-Trigger` header and creates the world membership
      (c) `PATCH /invites/{id}/decline` updates the invite status to `DECLINED`
      (d) `GET /worlds` with 0 invitations does not render the section
      (e) Accepting the last invitation returns the OOB section-removal fragment
- [ ] Legacy `home/` files and `homepage.css` import are removed.
      `mvn clean compile` passes.
- [ ] `mvn test` passes.
- [ ] No inline styles. No `/docs-css` (legacy) classes on the new component.
- [ ] `/docs-product` design tokens used throughout the new CSS.

## Out of scope

- Dedicated `/invitations` page
- Notification badge / dropdown in header (deferred)
- Toast primitive (reusing `notice notice--*`)
- Historical view of declined/expired invitations
- Real-time push
- Sorting / filtering / pagination / bulk actions
- Sending invitations (world settings → members)
- Real `expires_at` column on the invite row (stays synthetic)

## Tech lead review

**Verdict:** Approved

**Notes:**

Previous blockers (notice primitive, world list refresh endpoint, synthetic
expiry, `styles.css:40` import, ResourceRow name-drop) all resolved.

Implementation-time clarifications to watch:

- Handler response composition must order the primary fragment (empty row)
  first, then OOB fragments (`#pending-invitations-section` removal when last,
  notice). HTMX processes them in order.
- Verify `handlePipeline` permits `HX-Trigger` header mutation at the
  success-branch callsite; fall back to `respondHtml` directly if not.
- Confirm the existing notice target id in `worldListPage` before wiring the
  OOB swap. Add `<div id="notice-container">` if absent (or whatever the
  existing convention is).
- Confirm `handleSearchWorlds` empty-query path returns all user worlds during
  implementation (30-second check).
