# UX Improvement Notes -- Landing Page

Reviewed: 2026-02-05

## Summary

The landing page communicates its core value proposition adequately -- within a few seconds a visitor understands this is a tool for organizing Minecraft projects. The layout is clean and uncluttered, and the three-theme system (Overworld/Nether/End) is a delightful touch for the target audience. However, the page has several significant usability problems: the primary call-to-action button has a critical contrast/interaction defect across all themes, the feature cards break badly on mobile viewports, and there are multiple accessibility gaps that need attention. Fixing the top 3-4 issues below would meaningfully improve conversion and trust.

---

## Critical Issues (Must Fix)

### 1. Microsoft Sign-In Button Text Is Nearly Invisible on Hover, Focus, and Dark Themes

**Where:** The "Sign in with Microsoft" button -- `landing-page.css`, lines 50-56

**Issue:** The CSS for `.sign-in-container a button.microsoft-sign-in` uses `all: unset` inside the `:hover`, `:focus-visible`, and `:active` pseudo-classes. This has two devastating effects:
1. It resets the text color from the hardcoded `#5E5E5E` to the inherited color (which on the End theme is a light yellow, and on hover in Overworld becomes nearly invisible against the teal background).
2. It completely destroys the focus indicator, making the button unreachable for keyboard users in a way that violates WCAG 2.4.7 (Focus Visible).

Additionally, the button text color is hardcoded to `#5E5E5E` (gray) against a teal background (`--clr-action`, approximately `#0EA8BD` in Overworld). The contrast ratio of gray `#5E5E5E` on teal `#0EA8BD` is roughly 2.2:1, which fails WCAG AA for normal text (requires 4.5:1). On the Nether and End dark themes, the button background becomes orange/lava, and on the End theme it becomes purple -- in both cases the hardcoded gray text is nearly unreadable.

**Impact:** This is the single most important interactive element on the page. A user who cannot read the button text or who sees the text disappear on hover will hesitate to click, directly reducing sign-up conversion. Keyboard-only users cannot perceive focus at all.

**Recommendation:** Remove `all: unset` from the `:hover`, `:focus-visible`, and `:active` pseudo-classes. Follow Microsoft's own sign-in button guidelines which specify white background with `#5E5E5E` text:

```css
button.microsoft-sign-in:hover {
    cursor: pointer;
    background-color: #F2F2F2;
    border-color: #6E6E6E;
}

button.microsoft-sign-in:focus-visible {
    cursor: pointer;
    outline: 2px solid var(--clr-border-focus);
    outline-offset: 2px;
}
```

---

### 2. Feature Cards Do Not Respond to Mobile Viewports -- 3-Column Grid Breaks

**Where:** `.landing-features` grid in `landing-page.css`, line 69

**Issue:** The feature cards use `grid-template-columns: repeat(3, minmax(0, 1fr))` with no responsive breakpoint. On a 375px mobile screen, each card is squeezed to roughly 100px wide. Text is truncated, "Resource Management" wraps awkwardly, and card descriptions become columns of 2-3 words each that are extremely difficult to read.

**Impact:** Mobile users (a significant portion of a gamer audience) see a nearly unusable feature section.

**Recommendation:** Add responsive breakpoints:

```css
@media screen and (max-width: 768px) {
    .landing-features {
        grid-template-columns: 1fr;
        gap: var(--spacing-sm);
    }
}
```

---

### 3. Two `<h1>` Elements on the Same Page (Accessibility)

**Where:** TopBar.kt line 52 has `<h1>MC-ORG</h1>`, and LandingPage.kt line 18 has `<h1>Organize Your Minecraft Projects</h1>`.

**Issue:** Two `<h1>` elements violates best practices for document outline structure. Screen readers and SEO crawlers expect a single `<h1>`.

**Recommendation:** Change the topbar brand to a `<span>` with a CSS class like `.top-bar-brand`, updating the `.top-bar h1` CSS selector accordingly.

---

## Major Issues (Should Fix)

### 4. Heading and Body Text Alignment Inconsistency on Mobile

**Where:** Hero section on viewports below ~768px

**Issue:** The heading and subtitle are left-aligned, while the sign-in button and helper text are centered. This creates a jagged, inconsistent visual flow on mobile.

**Recommendation:** Add `text-align: center` to `.landing-page`.

---

### 5. Feature Card Description Text Has Poor Contrast

**Where:** `.card-description` paragraphs inside feature cards

**Issue:** `opacity: 0.8` combined with `font-weight: lighter` drops the contrast below WCAG AA 4.5:1 ratio.

**Recommendation:** Remove `opacity: 0.8` and use `color: var(--clr-text-subtle)` instead.

---

### 6. No `max-width` Constraint on Hero Content

**Where:** `.landing-page` container on 1440px+ viewports

**Issue:** Text lines exceed 90 characters at wide viewports. Optimal readability is 45-75 characters.

**Recommendation:**

```css
.landing-page h1,
.landing-page > p {
    max-width: 600px;
}

.landing-features {
    max-width: 1000px;
}
```

---

### 7. Feature Cards Pushed Off-Screen on Small Viewports

**Where:** `.landing-page` rule in `landing-page.css`, line 11

**Issue:** `height: calc(100vh - var(--navbar-height))` forces the hero to fill the viewport, pushing feature cards entirely below the fold on smaller screens. Visitors may not realize there is content below.

**Recommendation:** Change to `min-height` instead of `height`:

```css
.landing-page {
    min-height: calc(100vh - var(--navbar-height));
    padding-top: var(--spacing-xl);
    padding-bottom: var(--spacing-xl);
}
```

---

## Minor Issues (Nice to Fix)

### 8. Missing Favicon

The browser requests `/favicon.ico` and receives a 404. Add a favicon to reinforce the brand.

### 9. Feature Card Titles Use `<p>` Instead of Heading Elements

`p("card-title")` in LandingPage.kt should be `h3("card-title")` for proper semantics.

### 10. Theme Toggle Icon Does Not Indicate Current Theme

The theme toggle shows the same icon regardless of active theme. Consider changing the icon or adding a tooltip.

### 11. Empty Alert Container in Accessibility Tree

The empty `<ul id="alert-container">` is announced by screen readers. Add `aria-hidden="true"` when empty.

### 12. Helper Text Below Sign-In Button Is Redundant

The text "Sign in with your Microsoft account to start organizing your Minecraft projects" repeats information already communicated. Replace with something new like "Free for all Minecraft players" or remove it.

### 13. Navbar Responsive Media Query Uses CSS Variable (Does Not Work)

In `navbar.css` line 85, `@media screen and (max-width: var(--media-lg))` does not work -- CSS variables are not supported in `@media` conditions. Replace with a hardcoded pixel value like `1024px`.

---

## Positive Observations

1. **Clear value proposition** -- the heading immediately communicates what the app does
2. **Themed design system** (Overworld/Nether/End) is creative and delightful for the target audience
3. **Minimal, focused page structure** follows a proven conversion pattern
4. **Well-chosen feature card icons** covering the core value pillars
5. **Microsoft sign-in** is natural for a Minecraft platform (shared accounts)
6. **Sticky navbar** provides consistent navigation
