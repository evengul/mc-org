---
name: docs-product
description: Design system reference for MC-ORG. Load when reviewing or implementing UI — covers color tokens, typography, spacing, component patterns, motion, and mobile behaviour.
user-invocable: false
---

# Design System Reference

MC-ORG design system. This is the ground truth for all UI implementation and review decisions.

Components are described as built. Where the Webapp coherence pass (Linear project P-MCO-63) has
decided a change that is not built yet, the entry says **Decided, not built** and names the issue
that builds it. Each design round updates this file in the same PR as its implementation.
*(Rewritten to the build on 2026-11-27, MCO-617. Until then it specified a PLAN / EXEC toggle, a
project card and a version-mismatch modal that no longer exist or were never built.)*

## Where the design system lives

Each thing lives in one place:

- **The values** are in `webapp/mc-web/src/main/resources/static/styles/design-tokens.css`, the
  file the app actually renders. Each colour carries its usage note as a comment on its own line.
- **The rules** are here: when to use what, the components and their classes, motion and mobile.
- **The components** are in the code: the DSL under `presentation/templated/dsl/` and the
  stylesheets under `static/styles/` (`docs-frontend` maps them).
- **[SEAM Design System](https://claude.ai/artifact/AYSR427dApGGdLuacvd5r5)** in Claude is
  derived from those and is never edited by hand. It is what design rounds draw with (the
  `claude-design` skill). `python3 webapp/scripts/design-tokens.py` produces its `tokens.json`
  (`css` for `tokens.css`), and this file is published unchanged as its README. When a colour or
  a rule changes, change it here or in `design-tokens.css` in a PR, then republish. The recipe is
  in `claude-design` → "The design system".

---

## Aesthetic Direction

**Tone: Utilitarian-technical with craft DNA.**

The aesthetic should feel like a **technical field notebook** — functional, dense-when-needed, never decorative for its own sake. Think: graph paper, worn UI, high-information density. Not a game UI, not a dashboard SaaS template.

The system must scale between "tap a counter on your phone while mining" and "scan a 200-row resource table on desktop."

---

## Color Tokens

All colors are CSS variables. Never use hardcoded hex values.

The palette is **"Daylight Field Notebook" (Overworld)** — a warm paper + sepia-ink
substrate drawn from the overworld's dirt/sand/wood, with accents borrowed from
overworld materials. It is a **light** theme (there is currently no dark theme).

| Role | Token | Hex |
|------|-------|-----|
| Page edge / app background (sand·dirt) | `--bg-base` | `#EBE1C8` |
| Page surface — cards, panels (paper) | `--bg-surface` | `#F7F0DE` |
| Raised — inputs, row hover, badges | `--bg-raised` | `#FCF7EA` |
| Rule lines, dividers, borders | `--border` | `#D6C6A2` |
| Emphasized rule (hover/focus border) | `--border-strong` | `#C2AE82` |
| Body, headings (sepia ink) | `--text-primary` | `#2A2218` |
| Labels, metadata (faded ink) | `--text-muted` | `#7A6B47` |
| Done items, dimmed content | `--text-disabled` | `#A99B78` |
| **CTA, active toggle, links — "act" (lapis)** | `--accent` | `#2C6E9E` |
| CTA hover | `--accent-hover` | `#245C85` |
| Accent wash — info callout, badges, focus ring | `--accent-muted` | `#D6E2EC` |
| Text/icon on an accent fill (cream) | `--on-accent` | `#FCF7EA` |
| **Success / status / progress — "done" (grass)** | `--green` | `#4F7A2B` |
| **Warning (wheat-gold)** | `--amber` | `#855A10` |
| **Danger / destructive (redstone)** | `--red` | `#A6321F` |
| Danger hover (darker redstone) | `--red-hover` | `#8B2A1A` |
| Progress bar fill (grass) | `--progress` | `#4F7A2B` |
| Done badge background | `--green-bg` | `#DEE8CB` |
| Blocked badge background | `--red-bg` | `#F0DACF` |

### Role discipline — one hue, one job

Every accent hue maps to **exactly one role**. This is what keeps the UI from
reading as generic SaaS, and it is enforced:

| Overworld material | Token(s) | Job |
|--------------------|----------|-----|
| Sand · dirt · wood | `--bg-*`, `--border*`, `--text-*` | Substrate: surfaces, rules, ink. **Brown is structural, never an accent.** |
| Lapis (sky·water) | `--accent`, `--accent-muted` | **Act**: primary/CTA, links, active states — and **info** (the quiet `--accent-muted` wash) |
| Grass · emerald | `--green`, `--progress` | **Done**: success, status, progress |
| Wheat-gold | `--amber` | Warning |
| Redstone | `--red` | Danger |

Rules that follow from this:

- **Secondary actions are not a second colour** — they're ink-on-paper
  (`.btn--secondary`: raised surface + border + ink). Resist colored secondary buttons.
- **Info is the *quiet* lapis**, not a new hue (we are out of colour-blind-safe hues):
  pale `--accent-muted` wash + ⓘ icon. Solid lapis = act, pale lapis = inform.
- **Primary (lapis) vs success (grass) are separated by hue**, so a CTA never reads
  as "done"; primary vs status badge are further separated by fill-vs-tint.

### Colour-blind constraint (load-bearing — the primary user is red-green colour-blind)

- The **only reliably distinguishable axis is blue↔yellow**. The act and status
  anchors (lapis, gold) live on it deliberately. Red/orange/brown/green all collapse
  together — which is why the CTA is **lapis, not copper/green** (copper read as red and
  collided with danger).
- **Never convey state by colour alone.** Success/warning/danger must *also* carry an
  icon or text label. Status badges already do this (they are text-labelled); callouts
  carry an icon. Honour this in any new component.

---

## Typography

Two fonts only. Never use other fonts.

- **IBM Plex Mono** (`--font-ui`): UI chrome, headings, badges, labels, nav, toggle states, section headers, button text, counter buttons
- **Inter** (`--font-body`): Body text, table content, prose, anything requiring legibility at small sizes

| Scale | Token | Font | Size | Weight | Usage |
|-------|-------|------|------|--------|-------|
| XS | `--text-xs` | Inter | 11px | 400 | Metadata, timestamps |
| SM | `--text-sm` | Inter | 13px | 400 | Table cells, secondary labels |
| Base | `--text-base` | Inter | 15px | 400 | Body text |
| UI | `--text-ui` | IBM Plex Mono | 13px | 500 | Badges, tags, code-like labels |
| Label | `--text-label` | IBM Plex Mono | 11px | 500 | Section headers (uppercase, tracked) |
| LG | `--text-lg` | IBM Plex Mono | 18px | 600 | Prominent inline figures — "next up" qty/item, hero card names |
| XL | `--text-xl` | IBM Plex Mono | 20px | 600 | In-page view titles — roadmap, import review, idea detail, wizard |

The page-level `h1` (`.page-heading__title`) sits one step above `--text-xl` at 24px and is
set on the class, not a token.

> There is **no `--text-heading`**. This table used to list one at "18–24px" — a range, not a
> value — and it was never defined in `design-tokens.css`, so the two call sites that trusted
> it rendered at the *inherited* size. MCO-388 folded them into `--text-xl`. Don't reintroduce
> the name; `--text-lg` / `--text-xl` are the two heading steps.

**Section headers** render as: uppercase, letter-spacing 0.08em, muted color. CSS class: `.section-label`. Example: `RESOURCES TO GATHER`.

---

## Spacing

Base unit: 4px. Always use tokens — never raw pixel values.

| Token | Value |
|-------|-------|
| `--space-1` | 4px |
| `--space-2` | 8px |
| `--space-3` | 12px |
| `--space-4` | 16px |
| `--space-5` | 24px |
| `--space-6` | 32px |
| `--space-8` | 48px |

Max content width: `1080px`. Mobile breakpoint: `768px`.

---

## Radius & Elevation

| Token | Value | Use |
|-------|-------|-----|
| `--radius` | 6px | Everything by default — **buttons always**, cards, inputs, panels |
| `--radius-pill` | 999px | Badges, chips, sort pills. **Never buttons.** |

Shadows are **ink-coloured** — `rgba(42, 34, 24, …)`, i.e. `--text-primary` at low alpha. A
shadow on warm paper is a smudge of the same ink, never a neutral grey halo. Two levels only:

| Token | Value | Use |
|-------|-------|-----|
| `--shadow-card` | `0 1px 2px rgba(42,34,24,0.06)` | A resting card — hero cards on worlds / project list |
| `--shadow-pop` | `0 4px 16px rgba(42,34,24,0.18)` | A layer floating above the page — dropdown, popover |

Do not invent a third level, and do not write a raw `box-shadow` for these two roles. Both
tokens exist because the values had already forked across inline `var(--x, fallback)` call
sites (MCO-388). The one sanctioned raw `box-shadow` is a focus/selection ring
(`0 0 0 3px var(--accent-muted)`), which is a ring, not elevation.

---

## Components

### The list toggle (project page)

The project page opens on its work list. One quiet button under NEXT UP offers whichever view is
not showing: "What I need · edit the list", then "← Back to the work list". It is a ghost button
(`.btn--ghost.btn--sm`), not a pair of tabs: two equal tabs said the two lists were equal, and the
work list is what the page is for (docs-ia, "Project Page"). CSS: `.list-resolution`,
`.list-resolution__option`.

*(This was a two-segment PLAN / EXEC pill, top-right of the project header, until MCO-481, then a
"What I need | How to make it" segmented pill.)*

### Status Badges

Pill badges. IBM Plex Mono. **The meaning is in the text**: a badge always says its state in
words, so it never relies on colour (the colour-blind rule below).

A project's state is `ProjectState`, labelled in `ProjectStateBadge.kt`. As built, its seven
values share four styles:

| State | CSS class | Background | Text |
|-------|-----------|------------|------|
| Pending | `.badge--not-started` | `--bg-raised` | `--text-muted` |
| Active | `.badge--in-progress` | `--accent-muted` | `--accent` |
| Done | `.badge--done` | `--green-bg` | `--green` |
| Paused, Cancelled, Archived, Decommissioned | `.badge--neutral` | — | — |

`.badge--blocked` (`--red-bg` / `--red`) exists for `BadgeStatus.BLOCKED`, which survives at the
roadmap's start card only.

**Decided, not built (Q4, after MCO-609):** Paused, Cancelled, Archived and Decommissioned each get
a visual of their own, and the `ProjectState` labels are used everywhere, the world tally's words
included.

**Glyphs:** a badge may carry a glyph beside its text only where the roadmap needs one in a dense
cell ("◐ building", "‖ paused", "○ planned" on TO BUILD rows). Elsewhere badges are text alone. *(This said "Text-only — no icons in badges"
until 2026-11-27, which the roadmap already broke and which contradicted the glyph-beside-colour
rule. Decision Q4 settled it.)*

### Buttons

IBM Plex Mono, `--text-ui` size, 8px vertical / 16px horizontal padding, **6px border-radius** (not pill).

| Variant | CSS class | Use |
|---------|-----------|-----|
| Primary | `.btn--primary` | Main CTA (Generate path, Import, Create) |
| Secondary | `.btn--secondary` | Supporting actions (Add resource, Add task) |
| Ghost | `.btn--ghost` | Tertiary / cancel |
| Danger | `.btn--danger` | Destructive actions |
| Small modifier | `.btn--sm` | Compact contexts |

### Progress Bar

- Height: 4px by default, 6px on the roadmap's start card and final-project panels (`.progress--lg`)
- Fill: `--progress` (accent), full green (`--green`) when complete
- Track: `--bg-raised`
- Border-radius: 2px
- Transition: 200ms ease on width change
- CSS: `.progress`, `.progress__fill`, `.progress__fill--complete`

### Work Row (project page)

One line of the work list (`WorkRow.kt`): item glyph, name and muted kind, the amount still to get,
and a "Log" strip for counting what you gathered. The short tail of the list renders as chips
under "Small jobs". A storage-tracked project shows "from N chests" instead of a typed count.

**Quantities (Q5):** counts are written in shulker boxes, stacks and items
(`formatItemCount`: "578 sb + 3 stacks + 32"); a plain "101,121" (`formatPlainCount`) is kept for
places where the breakdown is noise, such as a group header. `formatItemCount` assumes 64 to a stack
for every item. The item design round (MCO-608) settles where a plain count still belongs, and one
item component that draws glyph, name and kind everywhere (MCO-519).

*(A "Resource Row" with `+1 +64 +1782` counter buttons was specced here until 2026-11-27. 1782
was a typo for 1728, which is 27 × 64. The row it described belonged to the Projects tab, which
MCO-692 removed.)*

### Resource Table ("What I need")

Dense data table: the targets a player defined, behind the list toggle.

- Row height: 40px desktop / 48px mobile (stacked card on mobile)
- Alternating rows: `--bg-surface` / `--bg-raised`
- Sortable columns with sort indicator in muted accent
- Sticky header on overflow
- Table border: 1px `--border` around whole table, no vertical column dividers
- Inline edit: clicking a cell activates inline input — no modal
- Mobile: thead hidden, rows become stacked cards with `data-label` attributes on td
- CSS: `.data-table`, plus `.plan-resource-table` for the project's own (`PlanResourceTable.kt`)

### Warning / Notice Callout

Used for partial dependency notices and inline warnings. **Not a banner. Not full-width.**

- Left border: 3px `--amber`
- Background: `--bg-raised`
- Padding: `--space-3` / `--space-4`
- IBM Plex Mono `⚠` in amber, then Inter body text
- CSS: `.callout`, `.callout__icon`, `.callout__body`
- Info variant: `.callout--info` (lapis `--accent` rule + ⓘ icon — the "quiet lapis")
- Success variant: `.callout--success` (`--green` rule + ✓ icon)

### Navigation Header

**Desktop** — 56px tall. Logo left, breadcrumb center-left in IBM Plex Mono `--text-sm`, Ideas and Profile links right. No settings gear: settings lives on the thing it configures — a world tab, and a labelled button in the project header (docs-ia, "Navigation Chrome"; MCO-540).

Breadcrumb: `›` separators in `--text-disabled`. Current page: `--text-primary`, not a link. Prior segments: links.

**Mobile** — 56px tall. World name in IBM Plex Mono (a link to the world), Profile right. No
breadcrumb and no Ideas link.

Project page on a phone: a second header under it, with a back arrow left, the project name, and
`⚙ Settings` right.

CSS: `.app-header`, `.app-header__desktop`, `.app-header__mobile`, `.breadcrumb`,
`.breadcrumb__item`, `.breadcrumb__item--current`, `.breadcrumb__sep`,
`.project-detail__mobile-header`

**Decided, not built (Q3, MCO-607):** on a phone, a ☰ drawer (Worlds, Ideas, Profile, Settings,
Sign out) with the world name centred; on project pages the logo slot becomes ←, and the second
header goes. The shell design round draws it.

The shell has one breakpoint disagreement to settle in that round: the header switches between
its desktop and mobile layouts at 768px (`min-width: 768px` / `max-width: 767px`), while 28 other
rules use `max-width: 768px`, so at exactly 768px the header is desktop and the page under it is
mobile.

### Empty States

**Two-card world home** (the roadmap of a world with no projects):
- Two equal cards, same size, same prominence — never one dominant
- `--bg-surface` with `--border`
- No illustration — copy and structure carry weight
- Mobile: stack vertically, still equal size
- CSS: `.empty-state-cards`, `.empty-card`

**Generic centred empty state (roadmap, etc.):**
- Centred, IBM Plex Mono heading, muted body, single CTA
- CSS: `.empty-state`, `.empty-state__heading`, `.empty-state__body`

### Modal

- Backdrop: rgba black 60%, fade-in 150ms
- Modal: `--bg-surface`, `--border`, scale 0.97→1.0 150ms
- Max width: 440px
- CSS: `.modal-backdrop`, `.modal`, `.modal__heading`, `.modal__body`, `.modal__actions`

### Destructive actions

As built there are four ways to confirm a delete, and the shared confirm modal's button reads
"Delete" whatever it does. **Decided, not built (Q6, MCO-615):** three tiers.

| What | Confirm |
|------|---------|
| A project, world, account or idea | Type its name |
| Anything with children | A modal whose button names the verb |
| A reversible row action | No confirm; an undo toast |

Destructive actions live in a danger zone at the foot of the thing's settings page (`dangerZone`),
not in a page header. Project delete has moved there already.

### Task List

- Checkboxes: CSS-styled, 18px, border `--border`, checked: `--green` background
- Done task: `--text-disabled`, strikethrough
- CSS: `.task-list`, `.task-row`, `.task-checkbox`, `.task-row--done`

---

## Iconography

Use **Lucide** icons only. Icons only where they carry meaning not expressed by text.

| Icon | Usage |
|------|-------|
| `←` | Back navigation |
| `⚙` | Settings, on a button beside its label (`aria-hidden`); never alone, never on a nav tab |
| `☰` | Hamburger / drawer |
| `⚠` | Warning callout |
| `+` | Add actions (prefer text button over icon-only) |

No icon decorations on nav items. No custom icons, apart from the item glyphs, which draw
Minecraft items rather than UI actions (`itemGlyph`).

**Known violations:** the build has three icon systems besides the item glyphs: `lucide()`
(inline Lucide), `iconComponent()` (Material-style SVGs under `/static/icons/`) and bare Unicode
glyphs (⇄, ⤓, ◐). The rule above is the target. Which system survives is an open decision, not a
fix to make in passing.

---

## Motion

Minimal. Functional tool.

| Element | Animation |
|---------|-----------|
| Progress bar fill | 200ms ease on width change |
| Modal | 150ms fade-in + scale 0.97 → 1.0 |
| Page transitions | None |

This table used to carry a fifth row — "Resource counter increment: scale flash 1 → 1.1 → 1,
100ms". The `.counter-flash` class and its `@keyframes` existed in `resource-row.css`, but
**nothing ever applied the class** — no template, no JS — so the flash never shipped. It was
deleted in MCO-394 and the row removed with it. If the counter should flash on increment, that
is a feature to build (the class has to be added by JS on the htmx swap), not a spec to
re-document as though it were live.

---

## Layout Utilities

```css
.container     /* max-width 1080px, centered, horizontal padding */
.surface       /* bg-surface + border + border-radius */
.divider       /* 1px border-top in --border color */
.section-label /* uppercase, tracked, muted section header */
.subtle        /* muted small text */
.is-hidden     /* display: none */
.stack, .stack--xs      /* vertical flex + gap */
.cluster, .cluster--xs  /* horizontal flex + gap */
.mt-4          /* margin-top: var(--space-4) */
```

**There is no Tailwind-style utility layer.** `.flex`, `.flex-col`, `.items-center`,
`.justify-between`, `.gap-1`–`.gap-5`, `.w-full`, `.mt-5`, `.mb-4`, `.mb-5`, `.p-4` and `.p-5`
were listed here for a long time but had **zero users** in the whole codebase, and were deleted
in MCO-394. Layout is done by the component's own class; `.stack` / `.cluster` cover the
genuinely ad-hoc cases. Don't reach for a utility class that isn't in the list above, and don't
re-add the set — write the component's own rule instead.

---

## What Never Changes

These rules apply to every UI implementation without exception:

- No hardcoded colors — always CSS variables
- No inline `style =` — always CSS classes
- No fonts other than IBM Plex Mono and Inter
- No icons other than Lucide
- No pill border-radius on buttons — always `--radius` (6px)
- Status badges say their state in words; a glyph beside the words only in the roadmap's dense cells
- Warning callouts left-border only — never full-width banners
- Mobile minimum tap target: 36px for counter buttons, 44px for primary actions
- Each accent hue keeps one role: lapis = act/info, grass = done, gold = warn, redstone = danger, brown = structure
- Never convey state by colour alone — success/warning/danger also carry an icon or text label (the primary user is red-green colour-blind)

---

## Living Reference

A static gallery of every component on the live tokens lives at
**`/static/styleguide.html`** (no auth). Use it to eyeball palette/token changes
in one place before rolling them across real pages. Source:
`static/styleguide.html` + `static/styles/pages/styleguide.css`.

It is stale: it predates the roadmap's `rmg-*` vocabulary and the work row. Regenerate it after the
design rounds rather than patching it now.