---
name: docs-product
description: Design system reference for MC-ORG. Load when reviewing or implementing UI — covers color tokens, typography, spacing, component patterns, motion, and mobile behaviour.
user-invocable: false
---

# Design System Reference

MC-ORG design system. This is the ground truth for all UI implementation and review decisions.

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
| **Warning (wheat-gold)** | `--amber` | `#B0791A` |
| **Danger / destructive (redstone)** | `--red` | `#A6321F` |
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
| Heading | `--text-heading` | IBM Plex Mono | 18–24px | 600 | Page titles, project names |

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

## Components

### Plan / Execute Toggle

Most-used interactive element. Must look intentional, not like a form control.

- Pill shape, 2 segments, **fixed width** — not content-derived (no layout shift on toggle)
- Inactive: `--bg-raised` background, `--text-muted` text
- Active: `--accent` background, `--on-accent` text
- Text: IBM Plex Mono, 12px, uppercase (`PLAN` / `EXEC`)
- Position: top-right of project detail header on desktop; inline in mobile header
- Transition: 150ms ease-in-out on background + text color
- CSS: `.toggle`, `.toggle__btn`, `.toggle__btn--active`

### Status Badges

Pill badges. IBM Plex Mono. Text-only — no icons in badges.

| State | CSS class | Background | Text |
|-------|-----------|------------|------|
| Not Started | `.badge--not-started` | `--bg-raised` | `--text-muted` |
| In Progress | `.badge--in-progress` | `--accent-muted` | `--accent` |
| Done | `.badge--done` | `--green-bg` | `--green` |
| Blocked | `.badge--blocked` | `--red-bg` | `--red` |

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

- Height: 4px in resource rows, 6px in project list cards (`.progress--lg`)
- Fill: `--progress` (accent), full green (`--green`) when complete
- Track: `--bg-raised`
- Border-radius: 2px
- Transition: 200ms ease on width change
- CSS: `.progress`, `.progress__fill`, `.progress__fill--complete`

### Resource Row (Execute View)

Most critical mobile component.

```
[Item name]          [████████░░]    [32 / 64]
[source label]       [-64][-1][ 32 ][+1][+64][+1782]
```

- Minimum row height on mobile: **64px**
- Counter buttons: IBM Plex Mono, **36px minimum tap target** on mobile, 28px on desktop
- Progress bar: 4px tall, full width
- Source label: `--text-muted`, `--text-xs`, IBM Plex Mono
- Completed row: item name → `--text-disabled` + strikethrough, progress bar full green, buttons hidden/disabled
- Increment tiers: `+1`, `+64`, `+1782`. Matching decrements. Tapping count opens free-entry field.
- CSS: `.resource-list`, `.resource-row`, `.resource-row--complete`, `.resource-row__name`, `.resource-row__source`, `.resource-row__count`, `.counter-btns`, `.counter-btn`, `.counter-btn--pos`, `.counter-btn--neg`

### Resource Table (Plan View)

Dense data table.

- Row height: 40px desktop / 48px mobile (stacked card on mobile)
- Alternating rows: `--bg-surface` / `--bg-raised`
- Sortable columns with sort indicator in muted accent
- Sticky header on overflow
- Table border: 1px `--border` around whole table, no vertical column dividers
- Inline edit: clicking a cell activates inline input — no modal
- Mobile: thead hidden, rows become stacked cards with `data-label` attributes on td
- CSS: `.data-table`

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

**Desktop** — 56px tall. Logo left, breadcrumb center-left in IBM Plex Mono `--text-sm`, Ideas link and gear right.

Breadcrumb: `›` separators in `--text-disabled`. Current page: `--text-primary`, not a link. Prior segments: links.

**Mobile** — 56px tall. World name in IBM Plex Mono centered. Hamburger left, gear right.

Project detail mobile header: back arrow left, project name centered, toggle right.

CSS: `.app-header`, `.breadcrumb`, `.breadcrumb__item`, `.breadcrumb__item--current`, `.breadcrumb__sep`, `.mobile-header`

### Project Card (Project List)

- Background: `--bg-surface`, border: `--border`, border-radius: `--radius`
- Hover: border-color `--border-strong`, background `--bg-raised`
- Project name: IBM Plex Mono, `--text-base`, weight 600
- Meta: IBM Plex Mono, `--text-xs`, `--text-muted`
- CSS: `.project-card`, `.project-card__header`, `.project-card__name`, `.project-card__meta`

### Empty States

**Two-card world home:**
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
- Version mismatch modal uses amber treatment (`--amber` in heading icon)
- CSS: `.modal-backdrop`, `.modal`, `.modal__heading`, `.modal__body`, `.modal__actions`

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
| `⚙` | Settings |
| `☰` | Hamburger / drawer |
| `⚠` | Warning callout |
| `+` | Add actions (prefer text button over icon-only) |

No icon decorations on nav items. No custom icons in Phase 1.

---

## Motion

Minimal. Functional tool.

| Element | Animation |
|---------|-----------|
| Plan/Execute toggle | 150ms ease-in-out on background + text color |
| Resource counter increment | Scale flash: 1 → 1.1 → 1, 100ms |
| Progress bar fill | 200ms ease on width change |
| Modal | 150ms fade-in + scale 0.97 → 1.0 |
| Page transitions | None |

---

## Layout Utilities

```css
.container     /* max-width 1080px, centered, horizontal padding */
.surface       /* bg-surface + border + border-radius */
.divider       /* 1px border-top in --border color */
.flex          /* display: flex */
.flex-col      /* flex-direction: column */
.items-center  /* align-items: center */
.justify-between /* justify-content: space-between */
.gap-1 through .gap-5  /* gap using --space tokens */
.w-full        /* width: 100% */
.mt-4, .mt-5, .mb-4, .mb-5  /* margin utilities */
.p-4, .p-5    /* padding utilities */
```

---

## What Never Changes

These rules apply to every UI implementation without exception:

- No hardcoded colors — always CSS variables
- No inline `style =` — always CSS classes
- No fonts other than IBM Plex Mono and Inter
- No icons other than Lucide
- No pill border-radius on buttons — always `--radius` (6px)
- Plan/Execute toggle fixed width — never content-derived
- Status badges text-only — no icons inside badges
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