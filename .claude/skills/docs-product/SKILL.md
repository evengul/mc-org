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

| Role | Token | Hex |
|------|-------|-----|
| Page background | `--bg-base` | `#0F1117` |
| Cards, panels | `--bg-surface` | `#181C27` |
| Table rows hover, inputs | `--bg-raised` | `#1F2435` |
| Dividers, table borders | `--border` | `#2A2F42` |
| Body, headings | `--text-primary` | `#E8EAF0` |
| Labels, metadata | `--text-muted` | `#6E748A` |
| Done items, dimmed content | `--text-disabled` | `#3C4158` |
| CTAs, active toggle, links | `--accent` | `#4A7CFF` |
| Accent backgrounds, badges | `--accent-muted` | `#1E2D5C` |
| Completed resources, done badges | `--green` | `#3DBA7A` |
| Partial block notices, warnings | `--amber` | `#E8A225` |
| Destructive actions | `--red` | `#E05252` |
| Progress bar fill | `--progress` | `#4A7CFF` |
| Done badge background | `--green-bg` | `#1C3828` |
| Blocked badge background | `--red-bg` | `#3A1E1E` |

Dark theme only. Light theme is Phase 3.

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
- Active: `--accent` background, white text
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
- Info variant: `.callout--info` (accent color instead of amber)

### Navigation Header

**Desktop** — 56px tall. Logo left, breadcrumb center-left in IBM Plex Mono `--text-sm`, Ideas link and gear right.

Breadcrumb: `›` separators in `--text-disabled`. Current page: `--text-primary`, not a link. Prior segments: links.

**Mobile** — 56px tall. World name in IBM Plex Mono centered. Hamburger left, gear right.

Project detail mobile header: back arrow left, project name centered, toggle right.

CSS: `.app-header`, `.breadcrumb`, `.breadcrumb__item`, `.breadcrumb__item--current`, `.breadcrumb__sep`, `.mobile-header`

### Project Card (Project List)

- Background: `--bg-surface`, border: `--border`, border-radius: `--radius`
- Hover: border-color `#3a4060`, background `--bg-raised`
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