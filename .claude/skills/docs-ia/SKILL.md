---
name: docs-ia
description: Information architecture reference for Seam (MC-ORG). Load when evaluating feature scope, URL structure, navigation and header behaviour, what each page is for and what it contains, where a feature belongs, the project page's work list vs "What I need", the world's roadmap, settings pages, or the coherence-pass decisions (state vocabulary, quantities, destructive-action tiers).
user-invocable: false
---

# Information Architecture Reference

How Seam's pages fit together, and why. This file describes the app as it is built. Where a
decision has been made and not yet built, it is marked **Decided, not built** and names the issue
that builds it. Read those as the target, not as a description of the code.

*(This file was rewritten on 2026-11-27 (MCO-617) to match the build and the decisions of the
Webapp coherence pass (Linear project P-MCO-63). Until then its spine was a per-project,
server-persisted Plan/Execute toggle that MCO-481 had removed, and it described a mobile drawer
that was never built. Each design round updates this file in the same PR as its implementation.)*

---

## Core Principles

1. **Start flat, grow deep.** Every user lands on the same page structure. Complexity is reachable through contextual links. No persona gates, no mandatory setup flows.

2. **Resume the world, not the project.** The app knows which world you were in and opens it on its **roadmap**. Projects are not pinned in session. *(This said "lands you on the project list" until 2026-10-07. MCO-474 moved the world's home to the roadmap; MCO-586 moved the last links that still treated the list as home.)*

3. **The project page is the plan.** A project page opens on how to make it: the work list, ordered and grouped, with one next move. "What I need", the list of targets the player defined, is where that list is changed and the checklist read at the end. It is not a second equal view. *(This said "Plan and execute are views, not modes", with a per-project toggle stored server-side, until 2026-11-27. MCO-481 removed the toggle; decision Q2 of the coherence pass made the plan the page.)*

4. **One derivation, many surfaces.** A number shown in two places is computed once. Gathered / required is one definition everywhere (`GetGatherProgressStep`), and the roadmap's sections and the project page agree on it. Every extra view of the same data is another place a fix has to land: MCO-318 and MCO-505 were drift between two such views.

5. **Mobile is a first-class surface.** Every page must function well at narrow width with large tap targets. The roadmap's graph exists on mobile but is not optimised for it.

6. **No entry point bias.** When a world has no projects, both creation paths are presented as equals. Neither is the default.

---

## URL Structure

```
/                                                        → redirect (JWT logic)
/auth/sign-in                                            → landing + sign-in
/auth/sign-out?error=                                    → signed out, with the reason
/privacy                                                 → privacy page (public)
/worlds                                                  → world list
/worlds/:worldId                                         → redirect to the roadmap (non-permanent)
/worlds/:worldId/roadmap                                 → Roadmap tab (world home)
/worlds/:worldId/settings                                → Settings tab (world settings, admins)
/worlds/:worldId/projects/:projectId                     → project page
/worlds/:worldId/projects/:projectId?drill=:itemId       → the project page with one target's source chain open
/worlds/:worldId/projects/:projectId/settings            → project settings
/ideas                                                   → idea library
/ideas/:ideaId                                           → one idea
/ideas/create, /ideas/drafts/:draftId/edit               → draft list, draft form
/profile                                                 → profile (sign out, delete account)
/admin                                                   → superadmin only; 404 for everyone else
/link                                                    → device-code approval for the Seam Notebook mod
/api/v1/...                                              → the mod's JSON API (the one JSON carve-out)
```

No mode in the URL. Every URL is real, shareable, and bookmarkable. `?drill=` is one: a reload or a shared link opens the same chain.

Creating a project has no URL of its own (a `POST` to `/worlds/:worldId/projects`, which has no `GET` since MCO-692). "+ New project" opens a dialog on the roadmap, and every way of creating one project (blank, from a schematic, recording an existing farm, importing a single idea) lands on the new project's page. A batch import is the exception: it moves to the next design in the batch, and at the end back to where the batch started (MCO-459, `ImportQueue`). `/worlds/:worldId/roadmap#new` opens that menu on arrival, for links from outside the world such as the Worlds page.

Links that leave a project or a world-level flow without a destination of their own (deleting a project, the project page's mobile back button, cancelling an import review) go to the roadmap, because that is where the world opens. Links that name a tab on purpose (the tabs themselves) keep it.

The redirect on `/worlds/:worldId` is deliberately not permanent: a 301 is cached indefinitely by every browser that followed it, which is how the old one to `/projects` outlived the change.

---

## JWT Context Logic

- `activeWorldId` stored in JWT — set when entering a world, cleared on explicit world switch
- `activeProjectId` — **not stored**. Users switch projects freely; pinning creates friction.

### Redirect on `/`

```
if no activeWorldId → /worlds
if activeWorldId    → /worlds/:activeWorldId/roadmap
```

---

## Navigation Chrome

### As built

**Desktop (≥ 768px)** — one header (`appHeader` in `Navigation.kt`):

```
[Logo]   Worlds › [World Name] › [Project]               Ideas   Profile
```

**Mobile (< 768px)** — the default header carries no breadcrumb, and no Ideas link:

```
[World Name]                                             Profile
```

The world name links to the world (and so to its roadmap), because without it a phone has no way back out of a world's sub-pages.

The **project page on a phone** stacks a second header under that one:

```
←  [Project Name]                                    [⚙ Settings]
```

Its `←` goes to the roadmap, the same place as the world name in the desktop breadcrumb.

The app header carries no settings link. Settings belongs to the thing it configures: world settings is a world tab, and project settings is a labelled `⚙ Settings` button in the project's own header, on the phone header too. *(Until 2026-10-09 a bare ⚙ at the far end of the app header led to world settings, for admins, on the roadmap and project list only. MCO-540 moved it: the gear was too small and too far from the page it configured.)*

No persistent bottom nav.

### Decided, not built: one header with a drawer (Q3, MCO-607)

The phone has no way to reach Ideas, and the project page stacks two headers. The decision:

- A **☰ drawer** holding Worlds, Ideas, Profile, Settings and Sign out, with the **world name centred**.
- On project pages the logo slot becomes **←**, so the second header goes.
- ⚙ goes into the drawer (Q8).

The shell design round (MCO-607) draws it. Until it ships, the "As built" section above is what renders.

### World bar

Every world tab carries the same bar under the header (`worldBar` in `Navigation.kt`):

```
Roadmap · Settings                               [+ New project]
```

Roadmap is first and is where the world opens. "+ New project" sits in the bar rather than in a page's own toolbar. The Settings tab has no action, since creating a project is not a setting. **Settings is drawn for admins only**: its route is admin-gated, and a tab that answers 403 is worse than no tab.

### Breadcrumb by page

The breadcrumb locates the *world*; which section of it you are in is the tab. So every tab shares one breadcrumb, and a world name in a breadcrumb links to the roadmap.

| Page | Breadcrumb |
|------|-----------|
| World list | *(none)* |
| Roadmap tab | Worlds › [World Name] |
| Settings tab | Worlds › [World Name] |
| Project page | Worlds › [World Name] › [Project Name] |
| Project settings | Worlds › [World Name] › [Project Name] › Settings |
| Idea library | Ideas |
| One idea | Ideas › [Idea Name] |

---

## World Tabs: Roadmap and Settings

A world has one view of its projects, the **Roadmap**, which is also what it opens to. **Settings** (admins only) is configuration, not a view of the world.

Every project in a world is reachable from the roadmap:

| Where | Which projects |
|-------|----------------|
| FINISH THESE FIRST / TO BUILD | Farms and other work still to build before a final project; a row with anything gathered says "N / M gathered" under its state |
| Final-project panels, "+N more" | What the world drains into, with a percentage |
| NOT IN ANY CHAIN | Unfinished projects with no edge; the note is "N / M gathered" when the project has resources, else its task count |
| PRODUCING | Finished farms feeding the roadmap. Their one home (Q1) |
| STOPPED | Decommissioned farms |
| SHELVED (folded shut, at the foot) | Cancelled and archived projects |

Below the graph: the order to build in, and **Manual ordering**, the edges a player added by hand on top of the ones generated from farm output.

**Empty world**: the shared world empty state below, not a roadmap-specific one.

*(Until mc-org PR 500, 2026-10-05, the roadmap also had a Table view toggle. It was removed; the graph's TO BUILD list carries the ARIA table roles the table had stood in for.)*

### Why there is no Projects tab (MCO-692, 2026-11-26)

There was one, the Field Log, between Roadmap and Settings until MCO-692. MCO-529 had kept it (2026-10-07) for four things only it had: a resume hero with counters, rows that expanded in place (with "adopt all" chest counts), every project grouped by state including shelved ones, and gathered/required on every active project. The owner then judged it redundant beside the roadmap. The last two moved to the roadmap (the SHELVED section and the per-row "gathered" counts). The resume hero, the expanding rows and adopt-all were let go: counting from chests (MCO-540) does what adopt-all did.

Opening a world on the last-used tab (MCO-587) has no second view to open on now.

---

## Project Page

`/worlds/:worldId/projects/:projectId`. The plan for one project. Top to bottom:

```
[Project Name]                                       [⚙ Settings]
[State]  Location: X, Z   Produces …   Imported from …
[Overall gathered progress]

[What the world's plans ask of this farm]            (a running farm only, MCO-605)

NEXT UP — one move, and a way to ask for another      (hidden while a question is open)
[What I need · edit the list]

Needs attention — the choices the plan can't make for you, largest first
Collect / Gather / Hunt / Loot / Trade / Smelt / Craft / Other — the work list
Small jobs — the short tail, as chips
Worth a farm — demand big enough to build for, with designs to choose from
Prerequisites — farms still to build that this plan waits on
Answered for you — tag choices too small to ask about

Tasks — N / M                                         (collapsed)
```

- **The work list is the page.** It is grouped by what the player does (`ActivityGroup`), and each line is a `WorkRow` with a "Log" strip for counting.
- **"What I need"** replaces the work list in place when the quiet toggle is pressed, and "← Back to the work list" returns. It is the list of targets the player defined: where a resource is added, a quantity changed, or a schematic uploaded. The choice is kept per project in `sessionStorage`, so it survives `#project-content` re-renders but is not stored on the server.
- **A source chain** (`?drill=:itemId`, the "⇄" buttons) replaces `#project-content` with one target's tree and a node picker. It took the place of the production path page.
- **A resource row's detail** opens in the `#resource-panel` dialog: a slide-over on desktop, a bottom sheet on a phone.
- **Tasks** sit below everything and are collapsed.

*(The project page had Execute and Plan views with a toggle until MCO-481, then a "What I need | How to make it" pill. The pill became the one quiet button above, because two equal tabs said the lists were equal, and they are not.)*

Open (Q2): where adding a resource and changing a quantity live once "What I need" is no longer a lens. The project page design round (MCO-611) answers it. It waits on the state audit (MCO-609) and the tasks audit (MCO-610).

### Project Settings

`/worlds/:worldId/projects/:projectId/settings` holds what changes how a project *behaves* and is set once: today the **Chest Counts** switch (storage-tracked, MCO-540) and the **Danger zone** with Delete project. What a project *is* — name, state, location — stays inline in its header, where it is read every visit and edited often.

- A page of its own, not a panel, because more will land there and a project page is already the longest page in the app.
- Open to every world participant, like the inline fields: what is there changes how the project counts, which any member can already do by hand. The danger zone is drawn for admins only, matching the delete route's gate.
- Delete moved here from beside the project name (2026-10-09), one deliberate step further from a stray click.

---

## Settings Pages (Q8)

One stacked page per settings scope, with the **danger zone last**. World settings (general, invitations, members, Discord, server token, danger zone) and project settings share the shape. World settings has no tabs; the only tab strip on it is the invitations pills. The worlds/settings design round (MCO-614) revisits both, now that project settings exists.

---

## Ideas (the idea library)

`/ideas` — top-level, linked from the desktop header and from the world empty state.

**Decided (Q7): the library is called Designs**, settled in the Ideas design round (MCO-613, 2026-10-28). The build work, including the rename and the `/designs` URLs, is the *Idea System Overhaul* project (MCO-640 to MCO-646), not this file. Until that ships, the app says "Ideas", and this section describes what renders.

- A filterable card grid: version filter, search by name or produced resource.
- Importing happens on the idea's own page, not on a card. Its world select lists only the worlds whose version the idea supports, so there is no version-mismatch warning to show.
- Importing creates the project with its resources and no tasks, and lands on the new project's page, or on the batch's next review.

*(This section described import on every card, a version-mismatch modal and two default tasks until 2026-11-27. None of them were built; the compatible-worlds select replaced the modal.)*

---

## World Empty State

When a world has no projects, the roadmap shows `worldEmptyState`: a world is empty in exactly one way, so it answers in one way. Two equal-weight cards, no dominant CTA:

```
┌───────────────────────┐  ┌───────────────────────┐
│  Plan your own        │  │  Browse community      │
│  project              │  │  ideas                 │
│                       │  │                        │
│  [the three doors]    │  │  [Browse Ideas]        │
└───────────────────────┘  └───────────────────────┘
```

The first card carries the same three doors as the "+ New project" menu (from a schematic, blank, record an existing farm), so the empty and populated entry points read as one control.

Both cards: same size, same visual prominence. On mobile: stack vertically, still equal size. Once a world has any projects, this empty state is gone.

---

## Coherence-pass Decisions

Recorded on the Webapp coherence pass project (P-MCO-63, 2026-10-09). Each is the input to the design round that builds it.

### Project state: one vocabulary (Q4)

- **As built:** `ProjectState` has seven values: Pending, Active, Paused, Done, Cancelled, Archived, Decommissioned. Its labels (`ProjectStateBadge.kt`) are mapped onto four badge styles, so Paused, Cancelled, Archived and Decommissioned all look alike. The world tally on the Worlds page uses its own words ("in flight", "queued").
- **Decided, not built:** the `ProjectState` labels everywhere, the world tally's words included. Glyphs only where the roadmap needs one in a dense cell. Paused, Cancelled, Archived and Decommissioned each get a visual of their own.
- **First:** the state audit (MCO-609) asks what state, stage, type and progress are for and which a player should ever set, since much of it is derived from gathering. Tasks get their own audit (MCO-610).

### Quantities count in shulker boxes (Q5)

It is how players think: 100 shulker boxes of sand is a picture of the work, and 150,000 sand is not. One item component draws glyph, name and muted kind (MCO-519, MCO-608).

- **As built:** `formatItemCount` (`ItemQuantity.kt`) writes "578 sb + 3 stacks + 32", and assumes 64 to a stack for every item. `formatPlainCount` writes "101,121" where the breakdown is noise, such as a group header (MCO-478). Seam has no stack-size data.
- **Open:** where a plain count still earns its place, and stack sizes. The item design round (MCO-608) answers both.

### Destructive actions: three tiers (Q6)

- **Type the name** for a project, world, account or idea.
- **A modal whose button names the verb** for anything with children.
- **An undo toast** for reversible row actions.

Project delete has already moved into project settings. The tiers and the verb-named confirm button are MCO-615, which comes after the component sweep's modal decision (MCO-606).

### Admin (Q9)

`/admin` is linked from Profile for superadmins. The buttons that do nothing are removed until they work (MCO-619). Today nothing links to it.

### Components (Q10)

The shared helpers (buttons, modal, empty state, section heading) are adopted by a mechanical sweep (MCO-606) before the first design is ported, so every port goes through one path. See docs-frontend for what is adopted today.

---

## Progressive Disclosure Model

| Feature | How reached | Hidden from users who don't need it |
|---------|-------------|--------------------------------------|
| "What I need" | The quiet toggle on the project page | ✓ The work list opens first |
| Source chain | "⇄" on a row, or a source chip | ✓ Only when a player wants to change a source |
| Answered for you | Below the work list | ✓ Small choices are made for you, and listed after the work |
| Tasks | Below the plan, collapsed | ✓ Collapsed by default |
| Roadmap | What a world opens on; first world tab | ✗ It is the world's home |
| Shelved projects | SHELVED at the foot of the roadmap, folded | ✓ Folded shut; the count is always shown |
| Ideas | Desktop header + world empty state | ✓ The empty state disappears once projects exist |
