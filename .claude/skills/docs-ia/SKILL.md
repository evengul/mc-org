---
name: docs-ia
description: Information architecture reference for MC-ORG. Load when evaluating feature scope, URL structure, navigation behaviour, plan/execute toggle semantics, page content hierarchy, progressive disclosure model, or user personas.
user-invocable: false
---

# Information Architecture Reference

Complete IA specification for MC-ORG. This is the ground truth for all product and UX decisions.

---

## Core Principles

1. **Start flat, grow deep.** Every user lands on the same page structure. Complexity is reachable through contextual links. No persona gates, no mandatory setup flows.

2. **Resume the world, not the project.** The app knows which world you were in and opens it on its **roadmap**, the first of the world's two tabs. Projects are not pinned in session. *(This said "lands you on the project list" until 2026-10-07. MCO-474 moved the world's home to the roadmap; MCO-586 moved the last links that still treated the list as home. Opening on whichever tab you last used is parked as MCO-587.)*

3. **Plan and execute are views, not modes.** The toggle is a per-project preference stored server-side. A user can have execute view on one project and plan view on another simultaneously.

4. **Execute means doing work — including gathering.** Execute view is the primary interface for active work, which includes resource gathering. Plan view is for defining and structuring what needs to happen.

5. **Mobile is a first-class surface.** Every page must function well at narrow width with large tap targets. Deeper pages (production path, roadmap) exist on mobile but are not optimised for it.

6. **No entry point bias.** When a user has no projects, both creation paths are presented as equals. Neither is the default.

---

## URL Structure

```
/                                                        → redirect (JWT logic)
/worlds                                                  → world list
/worlds/new                                              → create world
/worlds/:worldId                                         → redirect to the roadmap (non-permanent)
/worlds/:worldId/roadmap                                 → Roadmap tab (world home)
/worlds/:worldId/projects                                → Projects tab
/worlds/:worldId/projects/:projectId                     → project detail
/worlds/:worldId/projects/:projectId/path                → production path
/worlds/:worldId/settings                                → world settings
/ideas                                                   → idea hub
/ideas/:ideaId                                           → idea detail
```

No mode in the URL. Plan/execute is a per-project preference stored server-side. Every URL is real, shareable, and bookmarkable.

Creating a project has no URL of its own. "+ New project" opens a dialog on either tab, and every way of creating one (blank, from a schematic, recording an existing farm, importing an idea) lands on the new project's page. `/worlds/:worldId/roadmap#new` opens that menu on arrival, for links from outside the world such as the Worlds page.

Links that leave a project or a world-level flow without a destination of their own (deleting a project, the project page's mobile back button, cancelling an import review) go to the roadmap, because that is where the world opens. Links that name a tab on purpose (the tabs themselves) keep it.

The redirect on `/worlds/:worldId` is deliberately not permanent: a 301 is cached indefinitely by every browser that followed it, which is how the old one to `/projects` outlived the change.

---

## JWT Context Logic

- `activeWorldId` stored in JWT — set when entering a world, cleared on explicit world switch
- `activeProjectId` — **not stored**. Users switch projects freely; pinning creates friction.
- Per-project view preference (`plan` | `execute`) stored in DB per user per project. Default: `execute`.

### Redirect on `/`

```
if no activeWorldId → /worlds
if activeWorldId    → /worlds/:activeWorldId/roadmap
```

---

## Navigation Chrome

### Mobile (< 768px)

Default header:
```
☰  [World Name]                    ⚙️
```

Project detail header:
```
←  [Project Name]          [Plan|Exec]
```

No persistent bottom nav. Navigation is breadcrumb/back + in-page contextual links.

### Desktop (≥ 768px)

Default:
```
[Logo]   Worlds › [World Name]               Ideas   ⚙️
```

Project pages:
```
[Logo]   Worlds › [World] › [Project]        Ideas   ⚙️
```

### World bar

Both world pages carry the same bar under the header (`worldBar` in `Navigation.kt`):

```
Roadmap · Projects                              [+ New project]
```

Roadmap is first and is where the world opens. "+ New project" sits in the bar rather than in either page's own toolbar, so it is reachable from both tabs.

### Breadcrumb by page

The breadcrumb locates the *world*; which section of it you are in is the tab. So both tabs share one breadcrumb, and a world name in a breadcrumb links to the roadmap.

| Page | Breadcrumb |
|------|-----------|
| World list | *(none)* |
| Roadmap tab | Worlds › [World Name] |
| Projects tab | Worlds › [World Name] |
| Project detail | Worlds › [World Name] › [Project Name] |
| Production path | Worlds › [World Name] › [Project Name] › Path |
| Idea Hub | Ideas |
| Idea detail | Ideas › [Idea Name] |

On a phone the project page has no breadcrumb; its `←` goes to the roadmap, the same place as the world name in the desktop breadcrumb.

---

## Plan / Execute Toggle

### Rules

- Appears **only** on project detail page. Not on: resources, path, roadmap, idea hub, settings.
- Per-project preference persisted in DB per user.
- On project list: session-level global preference (what do I want to see across all projects right now).

### Persistence model

| Surface | Persistence | Scope |
|---------|-------------|-------|
| Project list | Session | Global |
| Project detail | Database, per user per project | Per project |

---

## World Tabs: Roadmap and Projects

A world has two tabs because they answer two different questions, and one page answering both would do neither well.

| Tab | Answers | Reach for it when |
|-----|---------|-------------------|
| **Roadmap** (first, the world's home) | *What do I build next, and in what order?* | Planning: deciding what to start, seeing what a final project waits on |
| **Projects** | *What am I gathering now, and where is everything, including shelved projects?* | Gathering for one to three active projects; finding a project the roadmap does not draw |

Which one should be home depends on the stage of the world: the roadmap early, while planning; the Projects tab later, once you are gathering. A fixed default can't be right for both, which is why MCO-587 (open on the last-used tab) is parked rather than rejected.

### Why the Projects tab stays (MCO-529, 2026-10-07)

MCO-529 asked whether the tab answered anything the roadmap graph does not. Its creation doors don't count: "+ New project" and the world empty state are on both tabs (MCO-474), and bulk state changes exist on neither. What only the Projects tab has:

1. **The resume hero.** The newest active project with editable resource counters and the Seam Notebook mod's measured stock, so you can track gathering without opening the project.
2. **Rows that expand in place.** What to gather next, a name filter, a blocker callout, "adopt all".
3. **Every project, grouped by state, including shelved ones.** The roadmap leaves out cancelled, archived and decommissioned projects that sit in no chain (`GetWorldRoadmapPipeline`), so this tab is the only way back to them in the UI.
4. **Gathered/required progress for every active project.** The roadmap shows progress only for the "start here" project and the final projects.

If a change moves one of these four onto the roadmap, ask again whether the tab still earns its place. Every extra view of the same projects is another place a fix has to land (MCO-318 and MCO-505 were drift between two such views).

---

## Project Detail Page

### Execute view

Primary content is **resources**, not tasks. Structure:

```
[Project Name]                              [Plan | Execute]
[Status badge]  Location: X:120, Z:-430    [Progress bar]

─── Resources to Gather ────────────────────────────────────

[Search / filter]

  Iron Ingot     ████████░░  32 / 64      [+1][+64][+1782]   Source: Iron Farm
  Redstone Dust  ░░░░░░░░░░   0 / 8       [+1][+64]          Source: Manual gather
  Oak Planks     ██████████  16 / 16  ✓                      Source: Manual gather

─── Tasks ──────────────────────────────────────────────────

  ☐  Lay out foundation
  ☐  Place hoppers
  ☑  Dig out area
  [+ Add task]

─── Partial dependency notice ──────────────────────────────
  ⚠ 32× iron ingot will come from [Iron Farm] once running.
    Gather the remaining 32 manually in the meantime.
```

Key rules:
- Resources are primary. Tasks are below resources.
- Counter increment tiers: `+1`, `+64` (one stack), `+1782` (one double chest = 27 × 64). Matching decrements. Tapping count opens free-entry field.
- Partial dependency notice is **not a banner** — lower visual weight, bottom of resources section. Only shown when at least one resource comes from an unfinished project.
- Source shown on each resource row: manual gather, crafting, or named project.

### Plan view

Primary content is **resource definition** in a dense table:

```
[Project Name]                              [Plan | Execute]
[Status badge]  Location: X:120, Z:-430

─── Required Resources ─────────────────────────────────────

[+ Add resource]    [Generate path →]    [View path →]

  ┌─────────────────┬────────┬──────────────────────────┐
  │ Item            │  Qty   │ Source                   │
  ├─────────────────┼────────┼──────────────────────────┤
  │ Iron Ingot      │   64   │ Iron Farm (planned)      │
  │ Redstone Dust   │    8   │ Manual gather            │
  ...
  └─────────────────┴────────┴──────────────────────────┘

─── Produced Resources ─────────────────────────────────────

─── Dependencies ───────────────────────────────────────────
  Blocked by:   Iron Farm  (provides iron ingot)
  Blocks:       Auto Crafter Array, Hopper Array

─── Tasks ──────────────────────────────────────────────────
  (collapsed by default)
```

Key rules:
- Required resources: dense sortable table, not a stacked list. Scales to hundreds of rows.
- "Generate path" and "View path" CTAs sit above the resources table — this is where automation is accessed.
- Tasks collapsed by default in plan view.
- Dependencies show specific resource edges, not whole-project blocks.

---

## Production Path Page

`/worlds/:worldId/projects/:projectId/path`

Reached from plan view → "View path →". Not linked from execute view directly.

Pre-DAG-viz: step list with method, inputs, status per step.
Post-DAG-viz (future): rendered graph replaces step list. List stays as accessible alternative.

---

## Roadmap Page

`/worlds/:worldId/roadmap`

The first world tab, and what a world opens on (`/`, `/worlds/:worldId`, the Worlds page's "Open world").

**Empty world**: the shared world empty state below, not a roadmap-specific one. *(The roadmap used to have its own, whose only offer was a link to the project list.)*

**Shape**: a graph of every project in a chain, ordered by dependency depth, with a "start here" project, the final projects, and a "Not in any chain" list for projects with no edges. *(Until mc-org PR 500, 2026-10-05, it also had a Table view toggle. It was removed; the graph's TO BUILD list carries the ARIA table roles the table had stood in for.)*

---

## Idea Hub

`/ideas` — top-level, always accessible from nav.

- Filterable card grid: version filter, search by name or produced resource
- Each card: farm name, produced resources + rate, required resources count, version range, attribution
- "Import to [World]" action on each card

### Import behaviour

1. If version matches: import proceeds directly
2. If version mismatch: modal warning with option to proceed
3. On import: project created with resources pre-populated, two default tasks added:
    - `Gather all required resources`
    - `Build [idea name]`
    - Note on tasks: "These are starter tasks — replace them with your own checklist." (dismissible)
4. Redirect to new project detail in **execute view**

---

## World Empty State

When a world has no projects, **both tabs** show the same block (`worldEmptyState`): a world is empty in exactly one way, so it answers in one way. Two equal-weight cards, no dominant CTA:

```
┌───────────────────────┐  ┌───────────────────────┐
│  Plan your own        │  │  Browse community      │
│  project              │  │  ideas                 │
│                       │  │                        │
│  [Create project]     │  │  [Browse Idea Hub]     │
└───────────────────────┘  └───────────────────────┘
```

Both cards: same size, same visual prominence. On mobile: stack vertically, still equal size. Once a user has any projects, this empty state is gone.

---

## Progressive Disclosure Model

| Feature | How reached | Hidden from users who don't need it |
|---------|-------------|--------------------------------------|
| Plan view | Toggle on project detail | ✓ Default is execute |
| Production path | Plan view → "View path" | ✓ Only relevant after resources defined |
| Roadmap | What a world opens on; first world tab | ✗ It is the world's home |
| Projects tab | Second world tab | ✗ Always one click away |
| Idea Hub | Nav + world empty state | ✓ Empty state disappears once projects exist |

---

## User Personas

### Casual player
Uses execute view only. Imports from Idea Hub. Tracks resources by incrementing counters while mining. Never touches plan view, path, or roadmap. Needs a complete experience from Phase 1.

### Technical player
Uses both views. Defines resources manually, generates paths, checks the roadmap. May have multiple projects open simultaneously in different views. Values the per-project view persistence.

### Worker
Daily session: opens execute view, scans project list, tracks resources, closes app. Never needs planning features. May encounter partial dependency notices but doesn't need to act on them.

---

## Build Phases

**Phase 1 — Core (current target)**
JWT world resume, project list with plan/execute toggle, project detail with per-project toggle persisted in DB, resource tracking in execute view (counter per resource row), task checklist, breadcrumb nav, world empty state (two equal cards), Idea Hub with import (version range validation, version mismatch warning, default tasks).

**Phase 2 — Automation**
Inline resource definition in plan view, production path generation (list form), partial dependency notice on project detail, roadmap as dependency table, roadmap empty state CTA.

**Phase 3 — Visualisation + Team**
DAG visualisation on roadmap and path pages, task assignment to team members, worker role default view preference.