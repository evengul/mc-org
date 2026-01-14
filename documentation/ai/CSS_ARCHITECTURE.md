# CSS Architecture Documentation for AI-Driven HTML Refactoring

## Quick Reference for AI Agents

This documentation provides structured guidance for AI agents to refactor HTML using the new CSS architecture. Use this as a reference to understand component classes, utility patterns, and migration strategies.

## 🎯 Key Refactoring Principles

1. **Replace inline styles** with utility classes
2. **Convert custom CSS classes** to component classes
3. **Use semantic color variables** instead of hardcoded colors
4. **Apply consistent spacing** using the spacing system
5. **Implement responsive patterns** using grid/flex utilities

## 📁 Architecture Overview

```
styles/
├── reset.css           # CSS reset and normalization
├── root.css           # CSS custom properties (--clr-*, --spacing-*, etc.)
├── utilities.css      # Utility classes (u-flex, u-margin-*, etc.)
├── layout.css         # Grid, flexbox, container systems
├── styles.css         # Main import file
├── components/        # Reusable UI components
└── pages/            # Page-specific styles
```

## 🎨 Design Tokens Reference

### Colors (Use these variables instead of hex codes)
```css
/* Action/Primary Colors */
--clr-action                    /* Primary action color (diamond-500) */
--clr-action-hover              /* Action hover state */
--clr-action-active             /* Action active state */
--clr-action-selected           /* Action selected state */
--clr-action-subtle             /* Light action background */
--clr-action-subtle-hover       /* Light action background hover */

/* State Colors */
--clr-success                   /* Success state (green-500) */
--clr-success-hover             /* Success hover state */
--clr-danger                    /* Error/destructive actions (red-500) */
--clr-danger-hover              /* Danger hover state */
--clr-danger-active             /* Danger active state */
--clr-danger-subtle             /* Light danger background */
--clr-danger-moderate           /* Medium danger background */
--clr-warning                   /* Warning state (orange-500) */
--clr-warning-subtle            /* Light warning background */
--clr-warning-moderate          /* Medium warning background */
--clr-info                      /* Info state (lightblue-500) */
--clr-info-subtle               /* Light info background */
--clr-info-moderate             /* Medium info background */

/* Neutral Colors */
--clr-neutral                   /* Neutral gray (gray-700) */
--clr-neutral-hover             /* Neutral hover state */
--clr-neutral-selected          /* Neutral selected state */
--clr-neutral-subtle            /* Light neutral background */
--clr-neutral-moderate          /* Medium neutral background */

/* Surface/Background Colors */
--clr-bg-default                /* Main page background (white) */
--clr-bg-subtle                 /* Subtle background (gray-100) */
--clr-surface-default           /* Default surface (white) */
--clr-surface-subtle            /* Subtle surface (gray-50) */
--clr-surface-hover             /* Surface hover state */
--clr-surface-active            /* Surface active state */
--clr-surface-selected          /* Surface selected state */
--clr-surface-transparent       /* Transparent surface */

/* Text Colors */
--clr-text-default              /* Primary text (gray-900) */
--clr-text-subtle               /* Secondary text (gray-alpha-700) */
--clr-text-danger               /* Error text */
--clr-text-action               /* Action text color */
--clr-text-action-hover         /* Action text hover */
--clr-text-on-action            /* Text on action backgrounds */
--clr-text-on-danger            /* Text on danger backgrounds */
--clr-text-on-success           /* Text on success backgrounds */
--clr-text-on-warning           /* Text on warning backgrounds */

/* Border Colors */
--clr-border-subtle             /* Default subtle borders */
--clr-border                    /* Standard borders */
--clr-border-strong             /* Strong borders */
--clr-border-action             /* Action/focus borders */
--clr-border-action-hover       /* Action border hover */
--clr-border-success            /* Success borders */
--clr-border-danger             /* Error borders */
--clr-border-warning            /* Warning borders */
--clr-border-info               /* Info borders */
--clr-border-focus              /* Focus outline color */

/* Alternative Theme Colors */
--clr-alt-1                     /* Oak wood theme (oak-wood-400) */
--clr-alt-1-subtle              /* Oak wood subtle */
--clr-alt-2                     /* Stone theme (stone-400) */
--clr-alt-2-subtle              /* Stone subtle */
--clr-alt-3                     /* Water theme (water-500) */
--clr-alt-3-subtle              /* Water subtle */
```

### Spacing Scale (Use these instead of pixel values)
```css
--spacing-baseline: 2em         /* Base unit for all spacing */
--spacing-xxs                   /* 0.5em (baseline/4) */
--spacing-xs                    /* 0.67em (baseline/3) */
--spacing-sm                    /* 1em (baseline/2) */
--spacing-md                    /* 2em (baseline) */
--spacing-lg                    /* 4em (baseline * 2) */
--spacing-xl                    /* 6em (baseline * 3) */
```

### Typography Scale
```css
--text-base-size: 16px          /* Base font size */
--text-sm                       /* Smaller text */
--text-md                       /* Medium text */
--text-lg                       /* Large text */
--text-xl                       /* Extra large text */
--text-xxl                      /* Extra extra large text */
```

### Shadows
```css
--shadow-xs                     /* Extra small shadow */
--shadow-sm                     /* Small shadow */
--shadow-md                     /* Medium shadow */
--shadow-lg                     /* Large shadow */
--shadow-xl                     /* Extra large shadow */
--shadow-focus                  /* Focus outline shadow */
```

### Border Radius
```css
--border-radius-sm: 2px         /* Small radius */
--border-radius-md: 4px         /* Medium radius */
--border-radius-lg: 8px         /* Large radius */
--border-radius-full: 9999px    /* Fully rounded */
```

---

## 🧩 Component Combination Matrix

This matrix shows how to combine base components with modifiers and utilities for common patterns:

| Base Component  | + Modifier              | + Utility               | Result Example                        |
|-----------------|-------------------------|-------------------------|---------------------------------------|
| `.btn`          | `.btn--action`          | `.u-full-width`         | Full-width primary action button      |
| `.btn`          | `.btn--danger`          | `.u-margin-sm`          | Delete button with spacing            |
| `.btn`          | `.btn--ghost`           | `.u-flex u-flex-center` | Transparent centered button           |
| `.card`         | `.card--elevated`       | `.u-margin-lg`          | Raised card with large margin         |
| `.card`         | (none)                  | `.u-padding-md`         | Flat card with medium padding         |
| `.list__item`   | `.list__item--selected` | `.u-flex-between`       | Selected list item with space-between |
| `.list__item`   | `.list__item--danger`   | `.u-padding-sm`         | Danger-state item with padding        |
| `.form-control` | (none)                  | `.u-full-width`         | Full-width input field                |
| `.notice`       | `.notice--success`      | `.u-margin-md`          | Success message with margin           |
| `.notice`       | `.notice--danger`       | `.u-flex u-flex-center` | Centered error message                |
| `.badge`        | `.badge--success`       | `.u-margin-xs`          | Success badge with small margin       |
| `.badge`        | `.badge--info`          | (none)                  | Info badge no extra spacing           |

**Usage Pattern:**

```html
<!-- Base + Modifier + Utility -->
<button class="btn btn--action u-full-width u-margin-md">
    Save Changes
</button>

<!-- Base + Multiple Utilities -->
<div class="card u-padding-lg u-margin-md u-full-width">
    Card content
</div>

<!-- Base + Modifier + Multiple Utilities -->
<div class="list__item list__item--selected u-flex u-flex-between u-padding-sm">
    <span>Item name</span>
    <span>Status</span>
</div>
```

---

## 🔄 Before/After Refactoring Examples

### Example 1: Inline Styles to Design Tokens

**Before:**

```kotlin
div {
    style = "color: #666; margin: 16px; padding: 12px; background: #f5f5f5;"
    +"Content with inline styles"
}
```

**After:**

```kotlin
div("u-text-subtle u-margin-md u-padding-sm u-bg-subtle") {
    +"Content with utility classes"
}
```

**Rationale**: Uses semantic color (`u-text-subtle`), spacing scale (`u-margin-md`, `u-padding-sm`), and background
token (`u-bg-subtle`)

---

### Example 2: Custom Button to Component Class

**Before:**

```kotlin
button {
    style = "padding: 12px 24px; background: #007bff; color: white; border: none; border-radius: 4px;"
    +"Submit"
}
```

**After:**

```kotlin
button(classes = "btn btn--action") {
    +"Submit"
}
```

**Rationale**: Component class handles all styling, consistent with design system

---

### Example 3: Custom Card Layout to Component

**Before:**

```kotlin
div {
    style = "padding: 20px; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);"
    h3 { +"Title" }
    p { +"Content" }
}
```

**After:**

```kotlin
div("card card--elevated") {
    h3 { +"Title" }
    p { +"Content" }
}
```

**Rationale**: `.card--elevated` provides padding, background, border-radius, and shadow automatically

---

### Example 4: Flexbox Layout to Utilities

**Before:**

```kotlin
div {
    style = "display: flex; justify-content: space-between; align-items: center; gap: 16px;"
    span { +"Left" }
    span { +"Right" }
}
```

**After:**

```kotlin
div("u-flex u-flex-between u-flex-align-center u-gap-md") {
    span { +"Left" }
    span { +"Right" }
}
```

**Rationale**: Utility classes for flexbox patterns, uses spacing scale for gap

---

### Example 5: Form Field to Component

**Before:**

```kotlin
input(type = InputType.text) {
    style = "width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px;"
}
```

**After:**

```kotlin
input(type = InputType.text, classes = "form-control u-full-width")
```

**Rationale**: `.form-control` handles padding, border, radius; `.u-full-width` for width

---

### Example 6: Color-Coded Status Badge

**Before:**

```kotlin
span {
    style = "padding: 4px 8px; background: #d4edda; color: #155724; border-radius: 4px; font-size: 0.875rem;"
    +"Active"
}
```

**After:**

```kotlin
span("badge badge--success") {
    +"Active"
}
```

**Rationale**: Component class provides semantic success styling

---

### Example 7: List Item with Hover State

**Before:**

```kotlin
div {
    style = "padding: 12px; background: #f8f9fa; margin-bottom: 8px;"
    onMouseOver = "this.style.background='#e9ecef'"
    onMouseOut = "this.style.background='#f8f9fa'"
    +"List item"
}
```

**After:**

```kotlin
div("list__item u-margin-sm") {
    +"List item"
}
```

**Rationale**: `.list__item` has hover state built-in, no JavaScript needed

---

### Example 8: Grid Layout

**Before:**

```kotlin
div {
    style = "display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px;"
    div { +"Column 1" }
    div { +"Column 2" }
}
```

**After:**

```kotlin
div("grid grid--cols-2 u-gap-md") {
    div { +"Column 1" }
    div { +"Column 2" }
}
```

**Rationale**: Grid component classes with responsive behavior

---

## 🧩 Component Classes for HTML Refactoring

### Buttons - Replace Custom Button Styles

**Before (Legacy):**
```html
<button style="background: blue; padding: 10px 20px;">Save</button>
<button class="custom-btn-action">Submit</button>
```

**After (New System):**
```html
<button class="btn btn--action">Save</button>
<button class="btn btn--action">Submit</button>
```

**Available Button Classes:**
```css
.btn                    /* Base button (required) */
.btn--action          /* Action color button */
.btn--neutral        /* Neutral color button */
.btn--danger           /* Red destructive button */
.btn--ghost            /* Transparent button */
.btn--icon-only        /* Square button for icons */

/* Size modifiers */
.btn--sm               /* Small button */
.btn--lg               /* Large button */
```

### Form Controls - Standardize Input Styling

**Before (Legacy):**
```html
<input type="text" style="padding: 8px; border: 1px solid #ccc;">
<select class="custom-select">...</select>
```

**After (New System):**
```html
<input type="text" class="form-control">
<select class="form-control">...</select>
```

**Available Form Classes:**
```css
.form-control          /* Base for input, select, textarea */
.form-control--sm      /* Small form control */
.form-control--lg      /* Large form control */

/* Input-specific variants */
input.input--error     /* Red border for errors */
input.input--success   /* Green border for success */

/* Select-specific variants */
select.select--error   /* Error state select */
select.select--success /* Success state select */

/* Textarea-specific variants */
textarea.textarea--compact  /* Smaller height */
textarea.textarea--tall     /* Larger height */

/* Checkbox/Radio variants */
input[type="checkbox"].checkbox--sm  /* Small checkbox */
input[type="radio"].radio--lg        /* Large radio */
```

### Layout System - Replace Manual Flexbox/Grid

**Before (Legacy):**
```html
<div style="display: flex; justify-content: space-between;">
<div class="custom-grid-container">
```

**After (New System):**
```html
<div class="u-flex u-flex-between">
<div class="grid grid--cols-3">
```

**Grid System Classes:**
```css
.grid                  /* Base grid container */
.grid--cols-1          /* 1 column */
.grid--cols-2          /* 2 columns */
.grid--cols-3          /* 3 columns */
.grid--cols-4          /* 4 columns */
.grid--cols-6          /* 6 columns */
.grid--cols-12         /* 12 columns */

/* Responsive variants */
.grid--md-cols-2       /* 2 cols on medium+ screens */
.grid--lg-cols-3       /* 3 cols on large+ screens */

/* Gap utilities */
.grid--gap-xs          /* Small gaps */
.grid--gap-lg          /* Large gaps */

/* Column spanning */
.col-span-2            /* Span 2 columns */
.col-span-full         /* Span all columns */
```

**Flexbox Utility Classes:**
```css
.u-flex                /* display: flex */
.u-flex-column         /* flex-direction: column */
.u-flex-between        /* justify-content: space-between */
.u-flex-center         /* justify-content: center */
.u-flex-align-center   /* align-items: center */
.u-flex-gap-sm         /* gap: var(--spacing-sm) */
```

### Container System - Standardize Widths

**Before (Legacy):**
```html
<div style="max-width: 1200px; margin: 0 auto;">
```

**After (New System):**
```html
<div class="container">
```

**Container Classes:**
```css
.container             /* Max 1200px, centered */
.container--sm         /* Max 768px */
.container--lg         /* Max 1400px */
.container--fluid      /* Full width */
```

## 🎯 Common UI Components

### Notice/Alert Boxes

**HTML Structure:**
```html
<div class="notice notice--info">
  <div class="notice__header notice__header--info">
    <h3 class="notice__title">Information</h3>
  </div>
  <div class="notice__body">
    <p>Your content here</p>
  </div>
</div>
```

**Available Notice Types:**
```css
.notice--info          /* Blue info box */
.notice--warning       /* Orange warning box */
.notice--success       /* Green success box */
.notice--danger        /* Red error box */
.notice--dashed        /* Dashed border style */
```

### List Components

**HTML Structure:**
```html
<div class="list">
  <div class="list__item">
    <div class="list__item-content">
      <h4 class="list__item-title">Item Title</h4>
      <p class="list__item-meta">Additional info</p>
    </div>
    <div class="list__item-actions">
      <button class="btn btn--sm">Edit</button>
    </div>
  </div>
</div>
```

### Card Components

**HTML Structure:**
```html
<div class="card card--elevated">
  <div class="card__header">
    <h3>Card Title</h3>
  </div>
  <div class="card__body">
    <p>Card content</p>
  </div>
</div>
```

## 🔧 Utility Classes for Quick Fixes

### Spacing Utilities - Replace Inline Margin/Padding

**Before:**
```html
<div style="margin-bottom: 16px; padding: 20px;">
```

**After:**
```html
<div class="u-margin-bottom-md u-padding-lg">
```

**Available Spacing Utilities:**
```css
/* Margin utilities */
.u-margin-0            /* Remove all margin */
.u-margin-top-sm       /* Small top margin */
.u-margin-bottom-md    /* Medium bottom margin */
.u-margin-left-auto    /* Auto left margin (centering) */

/* Padding utilities */
.u-padding-xs          /* Extra small padding */
.u-padding-sm          /* Small padding */
.u-padding-md          /* Medium padding */
.u-padding-lg          /* Large padding */
```

### Layout Utilities - Quick Layout Fixes

```css
.u-width-full          /* width: 100% */
.u-width-auto          /* width: auto */
.u-flex-1              /* flex: 1 1 0% (grow) */
.u-text-center         /* text-align: center */
.u-text-nowrap         /* white-space: nowrap */
.u-cursor-pointer      /* cursor: pointer */
```

### Stack and Cluster Layouts

**Stack (Vertical Spacing):**
```html
<div class="stack stack--md">
  <div>Item 1</div>
  <div>Item 2</div>
  <div>Item 3</div>
</div>
```

**Cluster (Horizontal Spacing with Wrap):**
```html
<div class="cluster cluster--sm">
  <button class="btn">Button 1</button>
  <button class="btn">Button 2</button>
</div>
```

## 📋 HTML Refactoring Checklist

### 1. Remove Inline Styles
- [ ] Replace `style="display: flex"` with `.u-flex`
- [ ] Replace `style="margin: 10px"` with `.u-margin-sm`
- [ ] Replace `style="padding: 20px"` with `.u-padding-lg`
- [ ] Replace `style="background: #fff"` with `style="background: var(--clr-surface-default)"`
- [ ] Replace hardcoded colors with CSS custom properties

### 2. Update Form Elements
- [ ] Add `.form-control` to all `<input>`, `<select>`, `<textarea>`
- [ ] Add size variants (`.form-control--sm`, `.form-control--lg`) as needed
- [ ] Add state variants (`.input--error`, `.select--success`) for validation

### 3. Standardize Buttons
- [ ] Add `.btn` base class to all buttons
- [ ] Add appropriate variant (`.btn--action`, `.btn--neutral`, etc.)
- [ ] Add size modifier if needed (`.btn--sm`, `.btn--lg`)

### 4. Convert Layout Containers
- [ ] Replace custom containers with `.container` classes
- [ ] Convert manual grids to `.grid` system
- [ ] Use flexbox utilities (`.u-flex`, `.u-flex-between`) for alignment

### 5. Apply Component Patterns
- [ ] Convert alert boxes to `.notice` components
- [ ] Structure lists using `.list` components
- [ ] Wrap content in `.card` components where appropriate

### 6. Use Semantic Colors
- [ ] Replace hex colors with CSS custom properties
- [ ] Use `var(--clr-action)` for action actions
- [ ] Use `var(--clr-danger)` for destructive actions
- [ ] Use `var(--clr-text-subtle)` for neutral text
- [ ] Use `var(--clr-surface-subtle)` for subtle backgrounds

### 7. Implement Responsive Design
- [ ] Use responsive grid classes (`.grid--md-cols-2`)
- [ ] Apply responsive utility classes where needed
- [ ] Ensure mobile-first approach

## 🔄 Migration Examples

### Example 1: Button Group
**Before:**
```html
<div style="display: flex; gap: 10px;">
  <button style="background: #007bff; color: white; padding: 8px 16px;">Save</button>
  <button style="background: #6c757d; color: white; padding: 8px 16px;">Cancel</button>
</div>
```

**After:**
```html
<div class="cluster cluster--sm">
  <button class="btn btn--action">Save</button>
  <button class="btn btn--neutral">Cancel</button>
</div>
```

### Example 2: Form with Error States
**Before:**
```html
<div style="margin-bottom: 20px;">
  <input type="email" style="border: 2px solid red; padding: 10px; width: 100%;">
  <div style="color: red; font-size: 14px;">Please enter a valid email</div>
</div>
```

**After:**
```html
<div class="u-margin-bottom-lg">
  <input type="email" class="form-control input--error">
  <div style="color: var(--clr-text-danger); font-size: var(--text-sm);">Please enter a valid email</div>
</div>
```

### Example 3: Card Layout
**Before:**
```html
<div style="background: white; padding: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-radius: 8px;">
  <h3 style="margin-bottom: 15px;">Project Details</h3>
  <p style="color: #666;">Project description here</p>
</div>
```

**After:**
```html
<div class="card card--elevated">
  <div class="card__header">
    <h3>Project Details</h3>
  </div>
  <div class="card__body">
    <p style="color: var(--clr-text-subtle);">Project description here</p>
  </div>
</div>
```

## 🎯 AI Agent Instructions

When refactoring HTML:

1. **Scan for inline styles** - These are the highest priority to replace
2. **Identify component patterns** - Look for buttons, forms, cards, lists
3. **Apply systematic classes** - Use the component classes first, then utilities
4. **Maintain semantic structure** - Don't change the HTML structure unless necessary
5. **Use CSS custom properties** - Replace all hardcoded colors with design tokens from root.css
6. **Test responsive behavior** - Ensure layouts work on different screen sizes

## 📱 Responsive Breakpoints

```css
/* Mobile First - Base styles target mobile */
@media (min-width: 768px) { /* Medium screens and up */ }
@media (min-width: var(--media-lg)) { /* Large screens (1024px+) */ }
```

Use responsive utilities:
- `.grid--md-cols-2` for responsive grid changes
- `.u-flex-md-row` for responsive flex direction
- Apply mobile-first thinking to all layouts

This documentation serves as a comprehensive guide for AI agents to systematically refactor HTML using the new CSS architecture while maintaining consistency and improving maintainability.
