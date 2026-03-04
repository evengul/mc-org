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

2. **Resume the world, not the project.** The app knows which world you were in. It lands you on the project list — you pick up wherever you left off. Projects are not pinned in session.

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
/worlds/:worldId/projects                                → project list (world home)
/worlds/:worldId/projects/new                            → create project
/worlds/:worldId/projects/:projectId                     → project detail
/worlds/:worldId/projects/:projectId/path                → production path
/worlds/:worldId/roadmap                                 → roadmap
/worlds/:worldId/settings                                → world settings
/ideas                                                   → idea hub
/ideas/:ideaId                                           → idea detail
```

No mode in the URL. Plan/execute is a per-project preference stored server-side. Every URL is real, shareable, and bookmarkable.

---

## JWT Context Logic

- `activeWorldId` stored in JWT — set when entering a world, cleared on explicit world switch
- `activeProjectId` — **not stored**. Users switch projects freely; pinning creates friction.
- Per-project view preference (`plan` | `execute`) stored in DB per user per project. Default: `execute`.

### Redirect on `/`

```
if no activeWorldId → /worlds
if activeWorldId    → /worlds/:activeWorldId/projects
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

### Breadcrumb by page

| Page | Breadcrumb |
|------|-----------|
| World list | *(none)* |
| Project list | Worlds › [World Name] |
| Project detail | Worlds › [World Name] › [Project Name] |
| Production path | Worlds › [World Name] › [Project Name] › Path |
| Roadmap | Worlds › [World Name] › Roadmap |
| Idea Hub | Ideas |
| Idea detail | Ideas › [Idea Name] |

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

## Project List Page

### Execute view columns/cards

- Project name
- Task progress: `4 / 9 tasks done`
- Status badge
- Resource progress: `12 / 64 iron ingot gathered`
- Partial block indicator (only if a required resource comes from an unfinished project)
- Next incomplete task (truncated)

Sort: unblocked in-progress → unblocked not-started → partially blocked → fully blocked → done (collapsed).

### Plan view columns/cards

- Project name
- Resource definition status
- Production path status
- Dependency summary
- Location if set

Sort: incomplete plans first → complete plans by dependency depth.

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

Reached from project list → "View Roadmap →". Not a top-level nav item.

**Empty state**: Full page with CTA to define resources and generate paths. Not hidden.

**Pre-DAG-viz**: Dependency table — projects as rows, sortable by dependency depth. "Blocked By" cells name both the project and the specific resource.

**Post-DAG-viz (future)**: Interactive graph. Table stays as toggle-able alternative.

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

## World Home Empty State

When a user has no projects — two equal-weight cards, no dominant CTA:

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
| Roadmap | Project list → "View Roadmap →" | ✓ Understated link, not nav item |
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