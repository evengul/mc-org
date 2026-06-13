# Gathering Planner — developer handoff

A quantity-aware **gathering planner** for SEAM: import a Minecraft schematic, the
path generator produces a plan of everything you must gather (each item once,
ingredients before what they build), and you work it down. This package is the
design reference for re-implementing it in the production stack
(**server-rendered Kotlin `kotlinx.html` DSL + HTMX**, SEAM design system).

> Scope note: this is a **design prototype**, not production code. It models the
> UI/UX and the two persisted decisions. The actual path-generation engine
> (`mc-engine`) already exists; this does not re-implement it.

---

## What's in this folder

| Path | What it is |
|------|------------|
| `screens.html` | **Start here.** Flattened, framework-free static HTML of the 4 key states, using only real SEAM classes + `var(--*)` tokens. Maps 1:1 to `kotlinx.html` fragments. |
| `seam/` | The SEAM design-system stylesheets the screens depend on (tokens + css). Already present in the real app under `/static/styles/` — included here so `screens.html` renders standalone. |
| `reference/` | The original interactive prototypes (`*.dc.html`). Open in a browser to click through. `Gathering Planner.dc.html` = the hi-fi flow; `Gathering Planner — Wireframes.dc.html` = the full Round 1–5 exploration with rationale for every decision. |
| `README.md` | This file. |

---

## The flow (4 states)

```
IMPORT ──▶ SHARPEN ──▶ PLAN ◀──▶ DRILL (per item)
                          │
                          └─ lenses: List · Next up · Sessions
```

1. **Import** — upload a `.litematic` / `.schem` / `.nbt`. Returns the Sharpen view.
2. **Sharpen** — the plan already exists; a short list of **cold-start questions
   ranked by plan impact** ("declare your iron farm → −2,400 cobble"). Each is
   independent, answerable or skippable. This is the *only* gate, and only at cold
   start — never a recurring mode. "See the plan" (or "Skip") → Plan.
3. **Plan** — the working view, with three **lenses** over the same data:
   - **List** (default) — gather rows grouped by world **geography** (do neighbouring
     farms in one trip), then base smelt/craft.
   - **Next up** — one suggested move at a time + a "↻ something else" reshuffle.
   - **Sessions** — items bundled into suggested trips; **degrades to List** when the
     engine can't group confidently.
4. **Drill** — from any row's `⇄`, the item's **chosen chain** collapsed to the picked
   source per node, where you can **override** any node's source.

---

## Domain model (what a Plan is made of)

A plan is an ordered list of **activities**. Each activity ≈ one item to obtain:

| Field | Meaning |
|-------|---------|
| `item` | Minecraft item id (e.g. `hopper`, `raw_copper`) |
| `required` | quantity needed (the honest, rolled-up total) |
| `current` | quantity gathered so far (drives the counter + progress) |
| `source` | the chosen way to obtain it (mine / smelt / craft / barter / loot / **supplied**) |
| `group` | session phase — used for ordering (gather → smelt → craft …) |
| `area` | world location cluster, for the geography grouping in the List lens |
| `status` | derived: `resolved` / `supplied` / `raw_gather` / `open_tag` / `blocked` |
| `children` | for craftables, the sub-activities it consumes (the chain) |

**Counter tiers are Minecraft-native: 1 / 64 / 1728** (item / stack / double-shulker) —
never round decimals. `ResourceRow` renders `-1728 -64 -1 +1 +64 +1728` and goes
complete (strikethrough, buttons hidden) at `current >= required`.

### Status → presentation (colour is never the only signal)
| status | shown as |
|--------|----------|
| `resolved` / done | normal row / green progress |
| `supplied` | **Supplied** `badge--accent`, **no counter** (comes from a farm/linked project) |
| `raw_gather` | counter row |
| `open_tag` | amber — needs a concrete variant picked (e.g. "any planks" → Oak) |
| `blocked` | `callout--warning`, labelled "Blocked" |

---

## The two persisted decisions (the whole point)

The engine derives everything else; the user only ever persists **two kinds of override**.
Everything in the UI funnels to these:

1. **`tagChoiceByItem`** — resolving an **open tag** to a concrete item
   (`#planks` → `oak_planks`). Surfaced in Sharpen, in the Plan attention callout, and
   in the Drill picker. *One value per open tag.*
2. **`sourceByItem`** — **pinning a source** for a node when several exist
   (`obsidian` → `mine` vs `barter`; `sand`/`red_sand` for glass). Surfaced in the Drill
   view. *One value per overridden item; absence = use the engine's scored pick.*

> Pinning either one **re-derives** the plan (a pin can prune or introduce whole
> sub-chains — e.g. declaring an iron farm removes the cobble/smelt chain; bartering
> obsidian introduces a gold chain).

---

## Drill & the fan-out-aware picker

A target can be a **deep chain**, and most nodes have **many** candidate sources (there
can be ~1,000 ways to obtain sticks). So:

- The drill view shows the **chosen chain only**, collapsed to the picked source per
  node. Deep sub-chains fold to one line with a "+N steps" affordance.
- Each node shows how many sources it has. **1-way nodes** show "· 1 way" with no control.
  **Multi-source nodes** show a `⇄ {currentPick}` chip.
- Tapping the chip opens a picker that **scales to fan-out**:
  - **few sources (2–3):** a short radio list with trade-off notes.
  - **high fan-out:** a **search field + ranked "top picks"** (engine-scored, best
    starred) + "997 more · search to narrow".

---

## HTMX swap points (suggested)

Everything is a server-rendered fragment; no client state beyond the DOM.

| Action | Request | Swaps |
|--------|---------|-------|
| Import file | `POST /worlds/{id}/plan` (multipart) | → Sharpen fragment |
| Answer a sharpen question | `POST .../plan/sharpen/{q}` | the questions card + activity count |
| Skip / See the plan | `GET .../plan` | → Plan fragment |
| Switch lens | `GET .../plan?lens=list\|next\|sessions` | the plan body region |
| Counter ± | `POST .../activity/{item}/progress` `{delta}` | that `resource-row` (+ header progress via OOB) |
| Resolve open tag inline | `POST .../activity/{item}/tag` `{choice}` | re-derive → plan body |
| Open a row's chain | `GET .../plan/chain/{item}` | → Drill fragment |
| Open a node's sources | `GET .../plan/chain/{item}/sources/{node}` | that node (expands the picker) |
| Pin a source | `POST .../plan/chain/{item}/pin` `{node, source}` | re-derive → Drill (+ plan via OOB) |
| Back to plan | `GET .../plan` | → Plan fragment |

`hx-target` + `hx-swap="outerHTML"` on the smallest enclosing fragment; use
**out-of-band swaps** to keep the header progress bar and activity count in sync after a
counter step or a re-derive.

---

## SEAM components used (class names in `screens.html`)

All from the bound SEAM system — do not restyle raw HTML to imitate them; use the real
DSL builders / classes.

| Component | Class(es) | Notes |
|-----------|-----------|-------|
| Button | `.btn .btn--primary\|secondary\|ghost\|danger` (`.btn--sm`) | secondary is ink-on-paper, never a 2nd colour |
| IconButton | `.btn .btn--icon-only` + `aria-label` | the `⇄` drill control |
| Badge | `.badge .badge--accent\|neutral` / `.badge--{status}` | text only, never icons inside |
| Callout | `.callout`(`.callout--info\|success\|error`) + `.callout__icon` + `.callout__body` | left-border only; glyph always present |
| ProgressBar | `.progress`(`.progress--lg`) + `.progress__fill`(`--complete`) | 4px rows / 6px cards; full-green at 100% |
| Tabs (pills) | `.tabs .tabs--pills` + `.tab`(`.tab--active`) | the lens switch |
| ResourceRow | `.resource-row` + `.resource-row__main/head/name/count/source` + `.counter-btns` + `.counter-btn--neg\|pos` | the counter; tiers 1/64/1728 |
| Card | `.card` | paper surface base |

App shell: `.graph-paper` lays the signature sepia grid under everything.

---

## Voice & non-negotiables (from the SEAM guide)

- **No emoji, ever.** (The `⭳ ⇄ ⌂ ★ ⌕` glyphs here are placeholders for **Lucide line
  icons** — swap them for real Lucide at 2px weight in production.)
- **Status is labelled, never colour-only** — the primary user is red-green colour-blind;
  the only reliable axis is **blue↔yellow** (hence lapis CTA + amber warn).
- **IBM Plex Mono** for all chrome/labels/buttons; **Inter** for body/table content.
- Section headers: UPPERCASE, tracked, muted (`.section-label`).
- Real Minecraft nouns + quantities; thousands separators on large counts; **never** pill
  radius except badges and the Plan/Execute toggle.
- Terse, builder-to-builder copy. Warnings are specific and calm — no exclamation marks.

---

## Notable prototype-only shortcuts (fix in the real build)

- **`⇄` drill control is a sibling button** next to each `ResourceRow`, because the SEAM
  `ResourceRow` component has no trailing-action slot. In production, either add an
  optional trailing-action slot to `ResourceRow`, or render plan target rows with a
  variant that includes the drill control natively.
- **Pins are currently cosmetic** in the prototype (selecting a source updates the chip
  but doesn't re-derive the visible rows). In production the pin must trigger the
  re-derive described above.
- The "2 more" low-impact sharpen questions are stubbed (count only).
- **Execute mode** isn't built — but the plan's counter rows **are** the Execute view, so
  the Plan↔Execute `Toggle` is a small addition over the same `ResourceRow`s.
