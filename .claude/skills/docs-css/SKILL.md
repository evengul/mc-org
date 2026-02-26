---
name: docs-css
description: CSS component classes and layout utilities for MC-ORG. Use when writing HTML templates, choosing button/form/layout/notice/card classes, or avoiding inline styles.
user-invocable: false
---

# CSS Architecture Reference

CSS component classes, utility classes, and layout system for MC-ORG.

---

## Buttons

```kotlin
button(classes = "btn btn--action")    // Primary action (blue/diamond color)
button(classes = "btn btn--neutral")   // Secondary/cancel
button(classes = "btn btn--danger")    // Destructive action
button(classes = "btn btn--ghost")     // Transparent/icon button
button(classes = "btn btn--icon-only") // Square icon-only button

// Size modifiers (combine with any variant)
button(classes = "btn btn--action btn--sm")  // Small
button(classes = "btn btn--action btn--lg")  // Large
```

## Form Controls

```kotlin
input(classes = "form-control")        // text, email, number, date, etc.
select(classes = "form-control") { }
textarea(classes = "form-control") { }

// Size variants
input(classes = "form-control form-control--sm")
input(classes = "form-control form-control--lg")

// State variants
input(classes = "form-control input--error")    // red border
input(classes = "form-control input--success")  // green border
```

## Layout — Grid

```kotlin
div("grid grid--cols-2") { }    // 2 columns
div("grid grid--cols-3") { }    // 3 columns
div("grid grid--cols-4") { }
div("grid grid--cols-6") { }

// Responsive
div("grid grid--md-cols-2") { }   // 2 cols on medium+ screens
div("grid grid--lg-cols-3") { }   // 3 cols on large+ screens

// Gap
div("grid grid--cols-2 grid--gap-xs") { }
div("grid grid--cols-2 grid--gap-lg") { }

// Column span
div("col-span-2") { }     // span 2 columns
div("col-span-full") { }  // span all columns
```

## Layout — Flexbox Utilities

```kotlin
div("u-flex") { }
div("u-flex u-flex-column") { }
div("u-flex u-flex-between") { }                              // space-between
div("u-flex u-flex-center") { }                               // justify-content center
div("u-flex u-flex-align-center") { }                         // align-items center
div("u-flex u-flex-between u-flex-align-center") { }          // common header pattern
div("u-flex u-flex-gap-sm") { }                               // gap: var(--spacing-sm)
```

## Layout — Stack & Cluster

```kotlin
// Stack: vertical spacing between children
div("stack stack--md") {
    div { +"Item 1" }
    div { +"Item 2" }
}

// Cluster: horizontal spacing with wrap
div("cluster cluster--sm") {
    button(classes = "btn btn--action") { +"Save" }
    button(classes = "btn btn--neutral") { +"Cancel" }
}
```

## Container

```kotlin
div("container") { }                    // max-width 1200px, centered
div("container container--sm") { }     // max-width 768px
div("container container--lg") { }     // max-width 1400px
div("container container--fluid") { }  // full width
```

## Notices / Alerts

```kotlin
div("notice notice--info") {
    div("notice__header notice__header--info") {
        h3("notice__title") { +"Information" }
    }
    div("notice__body") { p { +"Message" } }
}
// Variants: notice--info, notice--success, notice--warning, notice--danger, notice--dashed
```

Quick inline:
```kotlin
div("notice notice--success") { +"Done!" }
div("notice notice--danger") { +"Error: something went wrong." }
div("notice notice--warning") { +"Warning." }
div("notice notice--info") { +"FYI." }
```

## List Component

```kotlin
div("list") {
    div("list__item") {
        div("list__item-content") {
            h4("list__item-title") { +"Project Name" }
            p("list__item-meta") { +"BUILDING · Planning" }
        }
        div("list__item-actions") {
            button(classes = "btn btn--sm btn--action") { +"View" }
            button(classes = "btn btn--sm btn--danger") { +"Delete" }
        }
    }
}
// Modifiers: list__item--selected, list__item--danger
```

## Card Component

```kotlin
div("card card--elevated") {
    div("card__header") {
        h3 { +"Card Title" }
    }
    div("card__body") {
        p { +"Content here" }
    }
}
// Variants: card (flat), card--elevated (shadow + border-radius)
```

## Badge

```kotlin
span("badge badge--success") { +"Active" }
span("badge badge--danger") { +"Error" }
span("badge badge--info") { +"Info" }
span("badge badge--warning") { +"Warning" }
```

## Spacing Utilities

```kotlin
div("u-margin-md") { }           // margin all sides (md = 2em)
div("u-margin-top-sm") { }
div("u-margin-bottom-lg") { }
div("u-margin-left-auto") { }    // push right
div("u-padding-xs") { }          // 0.5em
div("u-padding-sm") { }          // 1em
div("u-padding-md") { }          // 2em
div("u-padding-lg") { }          // 4em
```

## Other Utilities

```kotlin
div("u-width-full") { }           // width: 100%
div("u-text-center") { }
div("u-text-nowrap") { }
div("u-cursor-pointer") { }
div("u-flex-1") { }               // flex: 1 (grow)
```

---

## Design Tokens (for inline style= when truly needed)

```kotlin
style = "color: var(--clr-text-subtle);"
style = "background: var(--clr-surface-subtle);"
style = "color: var(--clr-text-danger);"
style = "font-size: var(--text-sm);"
```

Key tokens: `--clr-action`, `--clr-danger`, `--clr-success`, `--clr-warning`,
`--clr-text-default`, `--clr-text-subtle`, `--clr-surface-default`,
`--spacing-xs/sm/md/lg/xl`, `--shadow-sm/md/lg`, `--border-radius-sm/md/lg`

---

## Anti-patterns

```kotlin
// WRONG
div { style = "display: flex; justify-content: space-between;" }
// RIGHT
div("u-flex u-flex-between") { }

// WRONG
button { style = "background: #007bff; padding: 10px 20px; color: white;" }
// RIGHT
button(classes = "btn btn--action") { }

// WRONG
div { style = "margin: 16px; padding: 20px; background: #f5f5f5;" }
// RIGHT
div("u-margin-md u-padding-lg u-bg-subtle") { }
```
