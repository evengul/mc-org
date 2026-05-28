---
linear-issue: "MCO-129"
epic: "MCO-128"
status: approved
phase: 1
created: 2026-03-04
---

# Self-hosted Fonts + CSS Design Tokens

## Summary

Bundle IBM Plex Mono and Inter as woff2 files served from `/static/fonts/`. Create a new
`design-tokens.css` file defining all new design-system tokens (colors, typography, spacing,
layout constants, radius) and layout utility classes. Add a `DESIGN_TOKENS` entry to `PageStyle`
so the new file is loaded by `createPage()` alongside the existing CSS. Old CSS files and the
Google Fonts CDN link are not touched. Both systems are loaded simultaneously; distinct token
names prevent conflicts.

This is the foundation that every subsequent frontend rewrite feature depends on. It produces no
visible change to existing pages.

## User value

No direct user-facing change. Unblocks all downstream page rewrites that use the new design system.

Indirect value: self-hosted fonts eliminate the Google Fonts CDN dependency, removing a network
round-trip observable on slow connections and a third-party tracking vector. App renders correctly
in offline or firewalled environments.

## Scope

**In scope:**

- Download and bundle IBM Plex Mono (Regular 400, Medium 500, SemiBold 600) and Inter (Regular 400,
  Medium 500) as woff2 files under `/webapp/mc-web/src/main/resources/static/fonts/`
- New CSS file `design-tokens.css` at `/webapp/mc-web/src/main/resources/static/styles/design-tokens.css`
  containing:
    - `@font-face` declarations for all five font weights/families
    - All color tokens from docs-product (15 tokens)
    - All typography tokens (font families + sizes)
    - All spacing tokens (7 values)
    - Layout constants (max-width, breakpoint, radius)
    - `.section-label` utility class
    - Layout utility classes (Tailwind-style naming: `.container`, `.surface`, `.divider`, `.flex`,
      `.flex-col`, `.items-center`, `.justify-between`, `.gap-1` through `.gap-5`, `.w-full`,
      `.mt-4`, `.mt-5`, `.mb-4`, `.mb-5`, `.p-4`, `.p-5`)
- New `PageStyle.DESIGN_TOKENS` enum entry with `getPath()` mapping
- No changes to `createPage()` needed — `entries.toSet()` automatically includes new entry

**Not in scope:**

- Component CSS classes (`.btn--primary`, `.toggle`, `.resource-row`, etc.)
- Changes to existing CSS files
- Removal of Google Fonts CDN `<link>` tags (Feature 13)
- Template changes beyond PageStyle
- Kotlin HTML DSL components

## Behaviour

Existing pages: no visible change. All old tokens remain in `root.css`. All old styles load.
New tokens are defined but not referenced by any existing component.

New pages (Feature 2 onward): use `--bg-base`, `--font-ui`, `--space-4`, etc.

**Font loading during transition:** Every page loads both Google Fonts Roboto Mono (old CDN link,
unchanged) and self-hosted IBM Plex Mono + Inter (new `@font-face`). `font-display: swap` on all
new declarations prevents invisible text. Extra download ~200KB, temporary.

**CSS utility collisions:** `.container` and `.flex` are intentionally redefined. The new
`.container` uses 1080px max-width (old uses 1200px). Since `design-tokens.css` loads after
`layout.css` (higher enum ordinal), the new definition wins on any page. This is accepted —
the old layout.css `.container` max-width changes from 1200px to 1080px for all pages during
transition. All other new utility classes use Tailwind-style naming (`.flex-col`, `.items-center`,
`.gap-4`) which don't collide with existing BEM-style names (`.flex--col`, `.align--center`).

**Naming convention decision:** Tailwind-style for all new utilities. Old BEM-style and u-prefixed
utilities coexist during transition, removed in Feature 13 cleanup.

## Technical approach

### Font files

Download pre-subsetted Latin woff2 from Google Fonts CDN (IBM Plex Mono is Apache 2.0; Inter is
OFL). No build-time subsetting tooling needed.

Files under `/webapp/mc-web/src/main/resources/static/fonts/`:

```
ibm-plex-mono-400.woff2
ibm-plex-mono-500.woff2
ibm-plex-mono-600.woff2
inter-400.woff2
inter-500.woff2
```

### design-tokens.css

Single flat file on `:root`. No imports. Tokens and layout utilities only — no component styles.

```css
/* --- Font faces --- */

@font-face {
    font-family: 'IBM Plex Mono';
    font-style: normal;
    font-weight: 400;
    font-display: swap;
    src: url('/static/fonts/ibm-plex-mono-400.woff2') format('woff2');
}

/* ... (500, 600 weights for IBM Plex Mono, 400, 500 for Inter) */

/* --- Design tokens --- */

:root {
    /* Fonts */
    --font-ui: 'IBM Plex Mono', monospace;
    --font-body: 'Inter', sans-serif;

    /* Colors */
    --bg-base: #0F1117;
    --bg-surface: #181C27;
    --bg-raised: #1F2435;
    --border: #2A2F42;
    --text-primary: #E8EAF0;
    --text-muted: #6E748A;
    --text-disabled: #3C4158;
    --accent: #4A7CFF;
    --accent-muted: #1E2D5C;
    --green: #3DBA7A;
    --amber: #E8A225;
    --red: #E05252;
    --progress: #4A7CFF;
    --green-bg: #1C3828;
    --red-bg: #3A1E1E;

    /* Typography scale */
    --text-xs: 11px;
    --text-sm: 13px;
    --text-base: 15px;
    --text-ui: 13px;
    --text-label: 11px;

    /* Spacing (4px base) */
    --space-1: 4px;
    --space-2: 8px;
    --space-3: 12px;
    --space-4: 16px;
    --space-5: 24px;
    --space-6: 32px;
    --space-8: 48px;

    /* Layout */
    --max-width: 1080px;
    --breakpoint-mobile: 768px;
    --radius: 6px;
}

/* --- Section label --- */
.section-label {
    font-family: var(--font-ui);
    font-size: var(--text-label);
    font-weight: 500;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--text-muted);
}

/* --- Layout utilities (Tailwind-style) --- */
.container {
    max-width: var(--max-width);
    margin-inline: auto;
    padding-inline: var(--space-4);
}

.surface {
    background: var(--bg-surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
}

.divider {
    border-top: 1px solid var(--border);
}

.flex {
    display: flex;
}

.flex-col {
    flex-direction: column;
}

.items-center {
    align-items: center;
}

.justify-between {
    justify-content: space-between;
}

.gap-1 {
    gap: var(--space-1);
}

.gap-2 {
    gap: var(--space-2);
}

.gap-3 {
    gap: var(--space-3);
}

.gap-4 {
    gap: var(--space-4);
}

.gap-5 {
    gap: var(--space-5);
}

.w-full {
    width: 100%;
}

.mt-4 {
    margin-top: var(--space-4);
}

.mt-5 {
    margin-top: var(--space-5);
}

.mb-4 {
    margin-bottom: var(--space-4);
}

.mb-5 {
    margin-bottom: var(--space-5);
}

.p-4 {
    padding: var(--space-4);
}

.p-5 {
    padding: var(--space-5);
}
```

**`--text-heading` deferred:** docs-product says 18-24px range. Concrete token(s) defined in
Feature 2 when heading usage is first implemented.

### PageStyle enum

Add one entry with `getPath()` mapping:

```kotlin
enum class PageStyle {
    RESET, ROOT, DESIGN_TOKENS, TEST_PAGE, STYLESHEET;

    fun getPath(): String = when (this) {
        RESET -> "/static/styles/reset.css"
        ROOT -> "/static/styles/root.css"
        DESIGN_TOKENS -> "/static/styles/design-tokens.css"
        TEST_PAGE -> "/static/styles/pages/test-page.css"
        STYLESHEET -> "/static/styles/styles.css"
    }
}
```

### Static file serving

Ktor's `staticResources("/static", "static")` in `Routing.kt` serves everything under
`src/main/resources/static/`. woff2 MIME type (`font/woff2`) handled by Ktor's built-in
`ContentType` mapping. No configuration changes needed.

### Verified: no explicit pageStyles overrides

Grep confirmed: zero `createPage()` call sites pass explicit `pageStyles =`. All use the default
`entries.toSet()`, so adding the enum entry is sufficient.

## Sub-tasks

- [ ] Download woff2 files for IBM Plex Mono (400, 500, 600) and Inter (400, 500). Latin subset.
  Place under `/static/fonts/`. Verify each file is under 50KB.
- [ ] Create `design-tokens.css` with all `@font-face` declarations, `:root` tokens, `.section-label`,
  and all layout utilities as specified above.
- [ ] Add `PageStyle.DESIGN_TOKENS` enum entry with `getPath()` mapping.
- [ ] Verify coexistence: start app, load existing page, confirm identical rendering, no console
  errors, design-tokens.css loading in network tab, woff2 files served with HTTP 200.
- [ ] Run `mvn clean compile` and `mvn test` — both must pass with zero errors.

## Acceptance criteria

- [ ] Five woff2 files exist under `/static/fonts/` and serve at `/static/fonts/*.woff2` with HTTP 200
- [ ] Each woff2 file is Latin-subset and under 50KB
- [ ] `design-tokens.css` exists at `/static/styles/design-tokens.css`
- [ ] All 15 color tokens defined with exact hex values from docs-product
- [ ] All 7 spacing tokens defined with correct px values
- [ ] `--font-ui` resolves to `'IBM Plex Mono', monospace`
- [ ] `--font-body` resolves to `'Inter', sans-serif`
- [ ] All `@font-face` declarations use `font-display: swap` and reference self-hosted files
- [ ] Typography scale tokens: `--text-xs` (11px), `--text-sm` (13px), `--text-base` (15px),
  `--text-ui` (13px), `--text-label` (11px)
- [ ] Layout constants: `--max-width` (1080px), `--breakpoint-mobile` (768px), `--radius` (6px)
- [ ] `.section-label` class present with correct styles
- [ ] All layout utilities from docs-product present (Tailwind-style naming)
- [ ] `PageStyle.DESIGN_TOKENS` exists with correct `getPath()` mapping
- [ ] `createPage()` loads `DESIGN_TOKENS` by default via `entries.toSet()`
- [ ] No old CSS files modified or removed
- [ ] Google Fonts CDN links in Page.kt unchanged
- [ ] No new token name collides with existing `--clr-*` tokens in root.css
- [ ] `.container` and `.flex` intentionally override old definitions (accepted collision)
- [ ] Existing pages render with no visual regression beyond `.container` max-width change
- [ ] No browser console errors
- [ ] `mvn clean compile` passes
- [ ] `mvn test` passes

## Out of scope / deferred

| Item                                             | Where it belongs           |
|--------------------------------------------------|----------------------------|
| Component CSS (`.btn--primary`, `.toggle`, etc.) | Feature 2 (DSL foundation) |
| Google Fonts CDN link removal                    | Feature 13 (cleanup)       |
| Old `root.css` token removal                     | Feature 13 (cleanup)       |
| Three-theme system removal                       | Feature 13 (cleanup)       |
| `--text-heading` token(s)                        | Feature 2 (heading usage)  |
| Old layout.css / utilities.css class removal     | Feature 13 (cleanup)       |
| Lucide icon bundling                             | Separate task              |
| Light theme / `prefers-color-scheme`             | Phase 3                    |

## Tech lead review

Verdict: Changes required → resolved
Notes: (1) `getPath()` mapping included in spec. (2) CSS utility collision resolved: Tailwind-style
naming accepted, `.container` and `.flex` intentionally override old definitions during transition.
(3) Ktor static serving confirmed to handle woff2 out of the box. (4) Zero explicit `pageStyles =`
overrides confirmed by grep.
