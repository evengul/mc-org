---
name: docs-htmx
description: HTMX helper functions and interaction patterns for MC-ORG. Use when using hx* helper functions, writing HTMX attributes, implementing form submissions, delete-with-confirm, out-of-band swaps (including OOB swaps of <tr>/<td>/<tbody>/<thead> table elements which require a <template> wrapper), or inline editing patterns.
user-invocable: false
---

# HTMX Patterns Reference

HTMX helper functions and interaction patterns for MC-ORG.

Examples use the current system: IA routes (`/worlds/:worldId/...`, no `/app/` prefix — build with the
`Link` sealed interface), the `btn btn--primary/secondary/ghost/danger` classes, and `pageShell { }` for
full pages. For the DSL components (`primaryButton`, `modal`, `taskList`, `createAlert`, …), `Link` helpers,
and CSS classes, load **docs-frontend**.

---

## HTMX Helper Functions (`presentation/hx.kt`)

```kotlin
// HTTP methods
fun HTMLTag.hxGet(value: String)
fun HTMLTag.hxPost(value: String)
fun HTMLTag.hxPut(value: String)
fun HTMLTag.hxPatch(value: String)
fun HTMLTag.hxDelete(value: String)

// Delete with confirmation modal (drives the shared confirm-delete dialog pageShell injects)
fun HTMLTag.hxDeleteWithConfirm(
    url: String,
    title: String,
    description: String? = null,
    warning: String? = null,
    confirmText: String? = null,   // if set, user must type this string to enable Delete
)

// Targeting and swapping
fun HTMLTag.hxTarget(value: String)       // CSS selector for swap target
fun HTMLTag.hxSwap(value: String)         // swap strategy (see table)
fun HTMLTag.hxOutOfBands(locator: String) // hx-swap-oob: "true" or "<swap>:<selector>"

// Triggers, includes, indicators
fun HTMLTag.hxTrigger(value: String)      // "load", "click", "change", "keyup changed delay:300ms", ...
fun HTMLTag.hxInclude(value: String)      // CSS selector for extra inputs to include
fun HTMLTag.hxIndicator(value: String)    // CSS selector for an hx-indicator element

// Error routing (response-targets extension, loaded by pageShell)
fun HTMLTag.hxTargetError(value: String)              // hx-target-error  — target for any error response
fun HTMLTag.hxErrorTarget(target: String, code: String) // hx-target-{code} — e.g. code = "404", "422"

// Extensions
fun HTMLTag.hxExtension(value: String)    // hx-ext
```

No `hxConfirm` / `hxPushUrl` helper exists — for those set the attribute directly:
`attributes["hx-push-url"] = "/worlds/$worldId/projects?view=plan"`.

## HTMX Swap Strategies

| Value | Effect |
|-------|--------|
| `innerHTML` | Replace inner content (default) |
| `outerHTML` | Replace entire element |
| `beforeend` | Append to end of target |
| `afterbegin` | Prepend to beginning of target |
| `delete` | Remove target element |

## Response Helpers (`presentation/utils/htmxResponseUtils.kt`)

```kotlin
// HTMX redirect — sets HX-Redirect header (HTMX navigations only; blank page on direct hit)
suspend fun ApplicationCall.clientRedirect(path: String)

// Error responses — set HX-Retarget + HX-Reswap so the error lands in the right place
suspend fun ApplicationCall.respondBadRequest(errorHtml: String = "An error occurred",
                                              target: String = "#error-message", swap: String = "innerHTML")
suspend fun ApplicationCall.respondNotFound(errorHtml: String = "...", target = "#error-message", ...)
```

`respondHtml(html: String, status = OK)` (in `htmlResponseUtils.kt`) sends an HTML fragment.

---

## Common Patterns

### Form submission (create)

```kotlin
// Template
form {
    id = "create-project-form"
    hxPost(Link.Worlds.world(worldId).projects().to)   // POST /worlds/{id}/projects
    hxTarget("#project-card-list")
    hxSwap("afterbegin")
    hxTargetError(".form-error")

    input(classes = "form-control") { name = "name"; placeholder = "Project name" }
    p("form-error") {}
    button(classes = "btn btn--primary") { type = ButtonType.submit; +"Create" }
}

// Handler success: return the new card; it's prepended to the list
respondHtml(createHTML().div { projectCard(worldId, project) })
```

(For modal create flows prefer the `modalForm(...)` DSL — it wires `hx-post`/target/swap and auto-closes +
resets on success. See docs-frontend.)

### Update action (button trigger)

```kotlin
// Template
button(classes = "btn btn--secondary") {
    hxPatch("/worlds/$worldId/projects/$projectId/tasks/$taskId/complete")
    hxTarget("#task-row-$taskId")
    hxSwap("outerHTML")
    +"Complete"
}

// Handler returns the replacement element (same id as the target)
respondHtml(taskRowFragment(worldId, projectId, task))
```

### Delete with confirmation

```kotlin
button(classes = "task-row__delete-btn") {
    type = ButtonType.button
    hxDeleteWithConfirm(
        url = "/worlds/$worldId/projects/$projectId/tasks/$taskId",
        title = "Delete task",
        description = "\"${task.name}\" will be permanently deleted.",
        confirmText = "DELETE",   // optional: require typing to confirm
    )
    hxTarget("#task-row-$taskId")
    hxSwap("delete")              // remove the row on success
    +"×"
}
```

### Success feedback as a toast

The shared `#alert-container` (`<ul>`) is injected by `pageShell`. To show a toast, target it with a
`beforeend` swap and return a `<li>` built by `createAlert`:

```kotlin
// Triggering form/button: hxTarget("#alert-container"); hxSwap("beforeend")
respondHtml(createHTML().li {
    createAlert(id = "world-name-updated", type = AlertType.SUCCESS, title = "World name updated")
})
// SUCCESS/INFO alerts auto-close after 3s. Combine with OOB swaps to also update the page (below).
```

### Dynamic content load on page load

```kotlin
div {
    id = "task-list"
    hxGet("/worlds/$worldId/projects/$projectId/tasks")
    hxTrigger("load")
    +"Loading tasks..."
}
```

### Out-of-band swap (update multiple elements)

```kotlin
// Main target gets the list; #project-count is updated out-of-band in the same response
respondHtml(createHTML().div {
    id = "project-card-list"
    projectCardList(worldId, projects)

    span {
        id = "project-count"
        hxOutOfBands("true")
        +"${projects.size}"
    }
})
```

### Out-of-band swap of a table row (`<tr>`)

Browsers strip orphan `<tr>`/`<td>`/`<tbody>`/`<thead>`/`<tfoot>` elements during HTML fragment parsing, so an
OOB `<tr>` at the top level of the response is discarded before HTMX sees it. Wrap it in an HTML `<template>` —
HTMX unwraps the template and processes the contained OOB element.

**Use this whenever OOB-swapping any of:** `tr`, `td`, `th`, `tbody`, `thead`, `tfoot`, `col`, `colgroup`, `caption`.

```kotlin
import kotlinx.html.TEMPLATE

// Main target: panel source section (innerHTML swap)
// OOB target: #plan-row-{id} — a <tr> inside a different table
respondHtml(createHTML().div {
    id = "resource-panel-source"
    resourcePanelSourceSection(resource)

    // OOB table row — wrapped in <template> so the browser parser preserves it
    TEMPLATE(mapOf(), consumer).visit {
        tr {
            id = "plan-row-${resource.id}"
            hxOutOfBands("outerHTML:#plan-row-${resource.id}")
            planResourceRow(worldId, projectId, resource)  // renders td children
        }
    }
})
```

The `<template>` wrapper is invisible to the browser and to HTMX's swap logic — it exists purely to survive
HTML parsing. Do NOT put the `<tr>` at the response top level, and do NOT wrap it in a throwaway
`<table style="display:none">` (inline-style violation + needless DOM node).

If you find yourself using `hxOutOfBands(...)` with a `<tr>` selector (e.g. `"outerHTML:#plan-row-123"`), load
this skill — the `<template>` wrapper is mandatory.

### Include extra inputs in request

```kotlin
button(classes = "btn btn--secondary") {
    hxPost("/worlds/$worldId/projects/$projectId/tasks")
    hxTarget("#task-list")
    hxInclude("#task-form-inputs")   // pull inputs from another element into the request
    +"Add Task"
}
```

### Inline editing

```kotlin
// View mode
div {
    id = "project-description"
    span { +project.description }
    button(classes = "btn btn--ghost btn--sm") {
        hxGet("/worlds/$worldId/projects/$projectId/edit/description")
        hxTarget("#project-description")
        +"Edit"
    }
}

// Handler returns the edit form (replaces #project-description)
respondHtml(createHTML().div {
    id = "project-description"
    form {
        hxPut("/worlds/$worldId/projects/$projectId/description")
        hxTarget("#project-description")
        textarea(classes = "form-control") { name = "description"; +project.description }
        div("cluster") {
            button(classes = "btn btn--primary") { type = ButtonType.submit; +"Save" }
            button(classes = "btn btn--ghost") {
                hxGet("/worlds/$worldId/projects/$projectId/description")
                hxTarget("#project-description")
                +"Cancel"
            }
        }
    }
})
```

---

## Rules

- **GET endpoints** return full pages via `pageShell(pageTitle, user) { ... }`.
- **POST/PUT/PATCH/DELETE endpoints** return HTML fragments (NOT full pages).
- Response element `id` must match the `hxTarget` selector.
- Always specify `hxTarget` — never rely on defaults.
- Use semantic HTTP: GET=read, POST=create, PUT=replace, PATCH=partial, DELETE=remove.
- Build URLs with the `Link` interface (`Link.Worlds.world(id).projects().to`) — no `/app/` prefix exists.
- Import `kotlinx.html.stream.createHTML` — never `kotlinx.html.createHTML`.
