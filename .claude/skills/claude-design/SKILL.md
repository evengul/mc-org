---
name: claude-design
description: >
  Design rounds for Seam as Design artifacts on claude.ai, built on the SEAM
  Design System: how a surface is mapped and measured, how the canvas is made
  in the session with the Artifact tool, how the owner's comments are read,
  how the design system is kept derived from mc-org, and how a sketch is
  ported to kotlinx.html. Use when an issue needs a design sketch before the
  implementation issues can be written, when the owner gives a claude.ai
  artifact link or has commented on a canvas, or when design frames (4A, 1C,
  …) are to be ported. Covers all three Seam web surfaces, since they share
  one design system. Triggers on: "design round", "design sketch", "Claude
  Design", "Design artifact", "the canvas", "claude.ai/artifact/", "design
  frame", "handoff".
---

# Design rounds as Design artifacts

Design is made as a Design artifact on claude.ai, not locally. Two design
tools kept in sync is debt, so don't build a local substitute.

Since 2026-10-12 the design systems live in claude.ai, and the sketch is made
in the session with the Artifact tool. There is no brief pasted into the
standalone Claude Design, no `clip.exe` and no DesignSync round trip.
*(Until 2026-10-28 this skill described that older flow. MCO-613's round was
the first one made on a canvas.)*

## The round

1. **Map the surface.** Read the page, and have an Explore agent inventory
   its components: what each section shows in each state, exact copy, which
   role sees what (auth plugins and template checks), links and sub-pages,
   and the Plan/Execute toggle's effect where there is one. Read the
   neighbouring Linear issues and check their status: a neighbour that is
   Done may have changed the page the brief describes.
2. **Measure it on real data.** Query the worktree's Neon branch (a fork of
   production) and write down the numbers the page shows in each state. The
   sketch can't see the data, and one drawn on invented numbers hides the
   hard cases: the long name, the item with thirteen outputs, the empty
   group. MCO-613's measurements showed that every player but one saw a
   single design. That changed the first question the round asked.
   Re-query every figure you put on a board. MCO-613's first draft called
   White Stained Glass "Ice" and guessed two rates.
3. **Write the brief for yourself** (see "The brief" below). It is the
   session's working basis and what shows the owner you understood. It
   doesn't need to live anywhere, but `mco-<id>-brief.md` in the scratchpad
   keeps it across a compaction.
4. **Make the canvas.** `Artifact` with `action: "quickstart"` and
   `intent: "design"`, then follow the type's instructions from there.
   Attach **SEAM Design System** (see "The design system" below). It is
   installed on the canvas as `project/ds/seam/` (`tokens.json` and
   `tokens.css`, copied server-side) and recorded in `designSystems` in
   `project/canvas.json`. Each board links `ds/seam/tokens.css` first, then
   the canvas's own stylesheet.
   - Give boards titles with a code and a state ("1C · 13 outputs",
     "A · Designs — 1440"). Group the directions under a `title1` note per
     row ("A · Two shelves: yours, and the community's"). What each direction
     gives up goes in a sticky beside its row, not on a board.
   - Draw what another round owns as it is today, and mark it. MCO-613 drew
     the shell with a dashed rule labelled "shell · MCO-607". Draw an open
     decision as a hatched slot with its issue number (MCO-441 image,
     MCO-455 schematic), never as a designed feature.
   - Real Minecraft item names, real measured numbers, and made-up player
     names ("Pebblewick", "Mossbottom"). A design name that carries its
     author's name ("Booty's 128k …") gets the stand-in too.
5. **Judge the sketch against the code**, not against the brief alone. Grep
   for the copy, the states and the distinctions the sketch claims before
   correcting it. A sketch invents explanatory text, and it takes the
   brief's wording literally: a phrase like "the drawer owns the fields" can
   come back as every edit moved into a drawer that lost half the fields.
   Use the real copy for anything the product already says. MCO-613's
   import-review warning was first drawn with a made-up reason under the
   real heading.
6. **The owner comments on the canvas.** Read the comments with
   `ArtifactComments` (`action: "read"`, the canvas's `url`). Don't ask for
   feedback in chat.
   - You can only answer and resolve threads the owner sent to Claude. The
     others stay open. Say which ones those are, so the owner can send them
     to Claude or close them.
   - Before correcting, say which product decisions the correction makes on
     the owner's behalf, so they can be stopped.
7. **Next round:** change the boards on the same canvas. Don't make a new
   one. The type's instructions say how a board is read, changed and
   published.
8. **When the sketch is approved:** write what was decided into the issues
   and a status update on the project: the canvas URL, the boards and the
   frame codes (MCO-566 is the model). The canvas is linked from the issues,
   but it is not where a decision lives. Write the implementation issues with
   `/linear`, following `seam-prioritization`. One issue per phase, so a
   phase's PR doesn't close the rest (`commit` → "Linear keywords").

## Changes during implementation

If the owner changes direction after the sketch is approved, and the change
is structural, correct the boards before the code. Structural means a new
surface, a drawer instead of a page, or where an action sits. Sibling issues
build from the canvas and cite boards by code. If a board shows the old
design, the next session builds the old design.

- Correct or add boards on the same canvas, with a blue sticky saying what
  changed and which open questions it settles.
- Let the owner approve on the canvas before the code changes.
- Then rewrite the affected issues, as in step 8.

Copy, spacing and details go straight into the code and the issue. A board
takes about 10 minutes, which is less than building the wrong thing and
tearing it down.

## The design system

[SEAM Design System](https://claude.ai/artifact/AYSR427dApGGdLuacvd5r5) is
derived from mc-org and is never edited by hand (`docs-product` → "Where the
design system lives"). Three of its files come from the repo:

- `project/tokens.json`: `python3 webapp/scripts/design-tokens.py`. Its
  `meta.source` names the commit it was built from.
- `project/tokens.css`: `python3 webapp/scripts/design-tokens.py css`.
- `project/README.md`: the `docs-product` skill file, unchanged.

The values live in `design-tokens.css`, each colour with a usage note as a
comment on its own line. The script exits if a colour has none. The type
scale's roles, and the usage words for sizes, spacing and radius, are in the
script itself.

**Before a round:** check whether `design-tokens.css` or `docs-product`
changed since that commit:

```bash
git log <sha>..origin/master -- webapp/mc-web/src/main/resources/static/styles/design-tokens.css .claude/skills/docs-product/SKILL.md
```

If either did, republish the three files from master after merge. Read the
artifact's instructions first (`read` on its URL). The index
(`project/design-system.json`) goes in its own call, last, with a new
`lastChange` and every other key kept as read. Run the script as a plain
`python3 <absolute path> > <file>`. The worktree guard refuses it inside a
compound command. If a round needs a new rule or colour, change it in the
repo first, in a PR.

**Tokens drift silently.** Until 2026-10-28 the system was a hand-written
copy migrated from the standalone Claude Design, and its amber was still
`#B0791A` six weeks after MCO-402 darkened it to `#855A10`. Its components,
UI kit and uploads described the old app, and were removed that day. The
system holds only what the repo generates. Don't add files by hand: a
component that a round needs is drawn on the canvas, in the canvas's own
stylesheet.

You can't see the artifact page yourself: it needs a claude.ai login, and
you must not log in. Say in your report that the page itself hasn't been
seen.

## Read a sketch

Read a sketch from an earlier session with `Artifact` (`action: "read"`),
the canvas's `url` and a `path`:

- `project/canvas.json` first. The index has the boards under `boards`,
  with title and position, and the notes under `notes`. The titles are what
  the owner and the issues call the boards: "1C" is the board whose title
  starts "1C ·".
- Then each board you need, e.g. `project/C-Design.dc.html`. The markup may
  be a template (`<sc-for>`, `<sc-if>`, `{{ … }}`) with the states and copy
  as data in `renderVals()`, or literal markup per state. Read the data
  either way.
- `project/ds/seam/` is the design system as it was when the canvas was
  made. `project/seam.css` is what the canvas added itself.

## The brief

- A short paragraph on Seam and its players. The design system carries the
  tokens and rules, so the brief doesn't repeat them.
- The surface as it is today: order, sections, states as a table, and exact
  copy.
- The problem, preferably in the owner's own words from the issue.
- The measurements from step 2, per state.
- The questions the sketch should answer, not the solution.
- What is decided, what is only planned, and what is open. Draw a planned
  change. Leave a slot for an open one, and don't design it. MCO-613's first
  brief drew image upload, schematic hosting and a review queue as decided.
  All three were open in their issues.
- The frame from `docs-ia` and `docs-product`:
  - **Colour-blind constraint.** The primary user is red-green colour-blind,
    so state is never carried by colour alone.
  - **One hue, one job** (docs-product → "Role discipline").
  - **Mobile is first-class** below 768 px: large tap targets (44 px), and
    the mobile navigation chrome.
  - **Start flat, grow deep**: complexity goes behind contextual links, with
    no persona gates.
  - **Server-rendered HTML with HTMX swaps.** An interaction is a request
    that returns a fragment, so nothing should need client-side state to
    work.
  - **The Plan/Execute toggle**, where the page has one.
- The deliverable: 2–3 directions in the first round, each shown in named
  states with concrete numbers, plus a desktop view and a line on what each
  direction gives up.

## Pace

A bounded surface takes minutes, not days. An open exploration with three
directions takes about 20 minutes, and one finished direction about 10. Plan
for porting and testing being the dominant cost.

## Older formats

**DesignSync projects (`claude.ai/design/p/<uuid>`, until 2026-10).** Read
them with DesignSync (`get_project`, `list_files`, `get_file` with
`projectId`). `list_projects` lists design systems only. Read only: never
`finalize_plan`, `write_files` or `delete_files`. `get_file` returns JSON
with the HTML as an escaped string in `content`. Above roughly 50 KB the
response lands in a persisted-output file, so decode it with
`json.load(...)['content']`. The roadmap design (MCO-563/566) is one of
these.

**Handoff package (`api.anthropic.com/v1/design/h/<id>`).** A gzip
tarball. WebFetch saves it as `.bin`, so unpack it with `gunzip` and
`tar -x` in the scratchpad. Read `chats/chat1.md` first for the intent,
then `project/design_handoff_*/README.md` for the spec.

## Porting

- **Rebuild, don't copy.** The sketch is HTML; the page is kotlinx.html
  with the DSL components `docs-frontend` names. Map each element of the
  sketch to an existing component or class first. A new component is a
  decision to flag, not a by-product of the port.
- **Tokens by name.** Colours, spacing and type come from
  `design-tokens.css` by token name. If the sketch uses a value that isn't a
  token, it's either a token it approximated or a new token, and a new token
  is the owner's call. Never eyeball a hex value.
- **No inline styles** (CLAUDE.md → Critical Rules). Sketch styling becomes
  classes in the page's stylesheet.
- **Frame codes are anchors, not names.** Sketches name screens with codes
  (4A, 1C, F3). Give the component a descriptive name (`RoadmapToBuild`,
  `DesignShelf`) and cite the code in the PR and the issue.
- **Verify against the frame.** `/verify` on the same data the brief
  measured, and compare figure by figure (#496's Verified table is the
  model). Screenshot at 375 and 1440.
- **A part the sketch only shows on mobile is your call on desktop.** Look
  at it at 1440 before the owner does, and say in the report how you solved
  it.

## Other surfaces

The landing page (`seam-landing`) and the server dashboard
(`seam-server-dashboard`) carry synced copies of the same tokens, and a
round for them follows the same steps on the same design system. Drop the
HTMX and Plan/Execute points from the brief. Tokens change upstream in
mc-org first (workspace CLAUDE.md → "Design system").
