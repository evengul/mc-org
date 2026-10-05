---
name: claude-design
description: >
  Design rounds in Claude Design (claude.ai/design) for Seam: how a surface
  is mapped, how the brief and feedback are written and handed to the owner
  via the clipboard, how the sketch is read with DesignSync, and how it is
  ported to kotlinx.html. Use when an issue needs a design sketch before the
  implementation issues can be written, when the owner gives a Claude Design
  link or project UUID, or when design frames (4A, 4B, …) are to be ported.
  Covers all three Seam web surfaces, since they share one design system.
  Triggers on: "design round", "design sketch", "Claude Design", "brief for
  Claude Design", "feedback for Claude Design", "check it out with
  designsync", "claude.ai/design/p/", "design frame", "handoff".
---

# Design rounds in Claude Design

Design is made in Claude Design, not locally. Two design tools kept in sync
is debt, so don't build a local substitute.

Claude Code maps the surface, writes the brief and the feedback, reads the
sketch and ports it. The owner sits in Claude Design and pastes.

## The round

1. **Map the surface.** Read the page and have an Explore agent inventory
   its components: what each section shows in each state, exact copy, which
   role sees what (auth plugins), links and sub-pages, and the Plan/Execute
   toggle's effect where there is one. Read the neighbouring Linear issues
   and check their status: a neighbour that is Done may have changed the
   page the brief describes.
2. **Measure it on real data.** Pick fixture worlds in the worktree database
   and write down the numbers the page shows in each state (MCO-566's brief
   was measured against fixture world 21). The designer can't see the data,
   and a sketch drawn on invented numbers hides the hard cases: the long
   name, the item with seven sources, the empty group.
3. **Write the brief** to the scratchpad as `mco-<id>-brief.md`, and give
   the owner the command:

   ```
   ! iconv -f utf-8 -t utf-16le <absolute path to the file> | clip.exe
   ```

   This is WSL: `pbcopy` doesn't exist, and a plain `clip.exe < file` turns
   every `·`, `⚠` and `▸` into mojibake. The UTF-16 pipe is what survives.
   The owner pastes it into Claude Design and attaches `design-tokens.css`
   (`webapp/mc-web/src/main/resources/static/styles/`), the `docs-product`
   skill file, and screenshots of the page.
4. **Read the sketch with DesignSync.** The owner gives the project UUID
   (from `claude.ai/design/p/<uuid>`). Use `get_project`, `list_files` and
   `get_file` with `projectId`. `list_projects` lists design systems only and
   won't find ordinary projects. Read only: never `finalize_plan`,
   `write_files` or `delete_files` against a design project.

   `get_file` returns JSON with the HTML as an escaped string in `content`.
   Above roughly 50 KB the response lands in a persisted-output file. Decode
   it with `json.load(...)['content']` into `mco-<id>-round<N>.dc.html` in
   the scratchpad, in the first round too, or the next round has nothing to
   diff against.
5. **Judge the sketch against the code**, not against the brief alone. Grep
   for the copy, the states and the distinctions the sketch claims before
   correcting it. A sketch invents explanatory text, and it takes the
   brief's wording literally: a phrase like "the drawer owns the fields" can
   come back as every edit moved into a drawer that lost half the fields.
6. **Write the feedback** as `mco-<id>-round<N>.md` and give the same
   clipboard command. Before the owner pastes it, say which product decisions
   the feedback makes on their behalf, so they can be stopped.
7. **Next round:** fetch the file again and `diff` it against the previous
   one. Claude Design often overwrites the same filename, so the diff is the
   only way to see what changed. New rounds are often added as a new
   `<section>` at the top of the same file: read the newest section and the
   data it uses.
8. **When the sketch is approved:** record in the issue what was chosen,
   naming the project UUID, the file and the frame codes (MCO-566 is the
   model), and write the implementation issues with `/linear`. One issue per
   phase, so a phase's PR doesn't close the rest (`commit` → "Linear
   keywords").

## The brief

- A short paragraph on Seam and its players, pointing at `docs-product` for
  tokens and components.
- The surface as it is today: order, sections, states as a table, and exact
  copy. The designer doesn't see the code.
- The problem, preferably in the owner's own words from the issue.
- The measurements from step 2, per state.
- The questions the sketch should answer, not the solution.
- The frame from `docs-ia` and `docs-product`:
  - **Colour-blind constraint.** The primary user is red-green colour-blind;
    state is never carried by colour alone.
  - **One hue, one job** (docs-product → "Role discipline").
  - **Mobile is first-class** below 768 px: large tap targets, the mobile
    navigation chrome.
  - **Start flat, grow deep**: complexity behind contextual links, no
    persona gates.
  - **Server-rendered HTML with HTMX swaps.** An interaction is a request
    that returns a fragment, so nothing that needs client-side state to work.
  - **The Plan/Execute toggle**, where the page has one.
- The deliverable: 2–3 directions in the first round, each shown in named
  states with concrete numbers, plus one desktop view and a line on what
  each direction gives up.
- Obviously made-up names in example data. Real Minecraft item names are
  fine and better than invented ones.

## The feedback

- First, which direction goes forward, and what is borrowed from the others.
- Numbered corrections where the sketch differs from the product, with the
  real copy pasted in.
- New states for the next round, with concrete numbers.

## Pace

A bounded surface takes minutes in Claude Design, not days: open
exploration with three directions about 20 minutes, one finished direction
about 10. Plan for porting and testing being the dominant cost.

## The formats

**`.dc.html` (DesignSync).** A canvas where the markup is a template
(`<sc-for>`, `<sc-if>`, `{{ … }}`), and the states and copy live as data in
`renderVals()` in the `<script type="text/x-dc">` at the bottom. Read the
data there to see what each state says. `colors_and_type.css` is tokens,
and `uploads/` is what the owner attached. A project can hold several
artboards per file (`Roadmap Variants.dc.html` holds 4A, 4B and 4C).

**Handoff package (older links, `api.anthropic.com/v1/design/h/<id>`).** A
gzip tarball. WebFetch saves it as `.bin`, so unpack with `gunzip` and
`tar -x` in the scratchpad. Read `chats/chat1.md` first for the intent, then
`project/design_handoff_*/README.md` for the spec. `tokens.css` is the
values, and the JSX is prototypes exposing components on `window`.

## Porting

- **Rebuild, don't copy.** The sketch is HTML/JSX; the page is kotlinx.html
  with the dsl components `docs-frontend` names. Map each element of the
  sketch to an existing component or class first. A new component is a
  decision to flag, not a by-product of the port.
- **Tokens by name.** Colours, spacing and type come from
  `design-tokens.css` by token name. If the sketch uses a value that isn't a
  token, it's either a token it approximated or a new token, and a new token
  is the owner's call. Never eyeball a hex value.
- **No inline styles** (CLAUDE.md → Critical Rules). Sketch styling becomes
  classes in the page's stylesheet.
- **Frame codes are anchors, not names.** Sketches name screens with codes
  (4A, GridB, 1a). Give the component a descriptive name (`RoadmapToBuild`,
  `HandListSplit`) and cite the code in the PR and the issue.
- **Verify against the frame.** `/verify` on the same fixture worlds the
  brief measured, and compare figure by figure (#496's Verified table is the
  model). Screenshot at 375 and 1440.

## Other surfaces

The landing page (`seam-landing`) and the server dashboard
(`seam-server-dashboard`) carry synced copies of the same tokens. A design
round for them follows the same steps. Attach that surface's own copy of
the tokens, and drop the HTMX and Plan/Execute points from the brief.
Tokens change upstream in mc-org first (workspace CLAUDE.md → "Design
system").
