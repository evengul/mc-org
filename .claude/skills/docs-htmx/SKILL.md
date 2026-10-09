---
name: docs-htmx
description: HTMX helper functions and interaction patterns for MC-ORG. Use when using hx* helper functions, writing HTMX attributes, implementing form submissions, delete-with-confirm, out-of-band swaps and htmx partials (including swaps of <tr>/<td>/<tbody>/<thead> table elements, which need a partial), error responses and validation messages (fieldError, respondRefusal, respondInPlace), or inline editing patterns.
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

// After the element's own request answered < 400 (reset a form, close its dialog)
fun HTMLTag.hxOnSuccess(script: String)   // hx-on::after:request, guarded on event.target === this

```

No `hxConfirm` / `hxPushUrl` helper exists — for those set the attribute directly:
`attributes["hx-push-url"] = "/worlds/$worldId/projects?view=plan"`.

## htmx 4 (since MCO-545)

`pageShell` loads htmx **4.0.0** with `HTMX_CONFIG` (`Layout.kt`). What differs from htmx 2, and what
that config holds in place:

- **Error responses swap nothing into their target.** `noSwap` lists `4xx` and `5xx`, and that also
  silences `HX-Retarget`/`HX-Reswap` on an error. Out-of-band swaps and partials still run, which is
  how every error reaches the screen: see **Error responses** below.
- **Inheritance is implicit** via `implicitInheritance: true`, until containers that rely on it get
  `hx-target:inherited`. It covers `hx-get` and `hx-trigger` too, so **an element that loads on
  arrival must replace itself** (`hx-swap="outerHTML"`), never fill itself. Content swapped into an
  element carrying `hx-get` + `hx-trigger="load"` inherits both: every control in it re-fires that
  GET on arrival, into its *own* `hx-target`. On the project page that replaced `#project-content`
  with a picker, and the bulk-answer slot did the same (MCO-504, measured in the browser). See
  **Dynamic content load on page load** below.
- **Gone:** `hx-target-error`, `hx-ext`, `hx-params`, `hx-history`. No history
  snapshot is kept in the browser: Back re-requests the pushed URL, so an `hx-push-url` must be a
  real page.
- **Events are colon-separated.** `hx-on::after:request` (not `after-request`); its handler sees
  `ctx`, and there is no `event.detail.successful` or `xhr`. For "on success, do X" use
  `hxOnSuccess(...)`, which checks `ctx.response.status < 400` and `event.target === this` (events
  from htmx elements inside a form bubble to it).
- **`htmx:after:swap` fires on the element that sent the request**, not the target. Listen to
  `htmx:after:settle` for "something was swapped into X"; it fires on the target (the new element
  for `outerHTML`), before htmx processes the new content.
- **`htmx:confirm` must be settled.** A listener that prevents it has to call `issueRequest()` or
  `dropRequest()` (`confirmation-modal.js`), or the element's request queue waits forever.
- **`hx-delete` no longer sends the enclosing form's inputs.** Add `hx-include="closest form"` if
  a delete needs them; none does today.
- **A non-`outerHTML` out-of-band swap inserts the element's children**, so wrap what should land:
  `ul { hxOutOfBands("afterbegin:#alert-container"); li { createAlert(...) } }`.
- **`hx-swap-oob` inside a plain `<template>` is ignored.** htmx 4 reads only
  `<template hx type="partial">` (its `<hx-partial>`). Use `hxPartial(...)` (`HxOob.kt`), which also
  takes any selector, resolved from the element that sent the request.

## Error responses

An error the person can be shown says where it goes, from the server. Exactly four shapes, all in
`ErrorHandler.kt` (`ErrorRoutingSourceScanTest` fails on a 4xx/5xx answered any other way):

| What went wrong | Answer with | What the person sees |
|---|---|---|
| A field is invalid | a `ValidationError` through `handlePipeline` / `defaultHandleError` | the message next to the field (below); a plain form post gets the status page |
| Not allowed, not found, broke | `respondRefusal(status, title, message, alertId)`, or `defaultHandleError` | an alert in `#alert-container`; a page load gets the status page |
| The page is stale (a missing or unreadable parameter) | `respondBadRequest()` | an alert saying the page is out of date |
| Show part of the page again, e.g. a form with its complaint | `respondInPlace(html, target, swap, status)` | `html` swapped into `target` |

**Field messages.** Put `fieldError("<parameter>")` after each input, with the name the server
validates. The response (`fieldMessages`, `FieldError.kt`) is one partial per failed field aimed at
`find [data-error-for='<field>']` from the element that sent the request, plus an alert with every
message. `form-errors.js` sends a message whose `find` matched nothing to the nearest slot above the
sender, keeps in the alert only what no slot took, and empties a form's slots when it sends a new
non-GET request. So a field with no slot still shows its message, and two forms on one page may share
a field name. Messages read from `ValidationFailure.userMessage()` (`ValidationFailureMessages.kt`).

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

// Retarget a successful response. Errors: see "Error responses" above.
fun ApplicationCall.hxTarget(value: String)
fun ApplicationCall.hxSwap(value: String)
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

    input(classes = "form-control") { name = "name"; placeholder = "Project name" }
    fieldError("name")   // a validation message for `name` lands here
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

The placeholder replaces itself. `FlowContent.loadOnArrival(url)` in `presentation/hx.kt` is the
helper; a slot that has to stay (an id something else targets) wraps it rather than carrying the
request. Written out:

```kotlin
div {
    id = "task-list"                   // the response's own root carries the id from here on
    hxGet("/worlds/$worldId/projects/$projectId/tasks")
    hxTrigger("load")
    hxTarget("this")
    hxSwap("outerHTML")                // not innerHTML: what lands would inherit the load trigger
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
OOB `<tr>` at the top level of the response is discarded before htmx sees it. Send it as a partial: a
`<template>`'s content keeps table elements. A plain `<template>` with `hx-swap-oob` inside is **not**
read by htmx 4.

**Use this whenever swapping any of:** `tr`, `td`, `th`, `tbody`, `thead`, `tfoot`, `col`, `colgroup`, `caption`.

```kotlin
import app.mcorg.presentation.hxPartial
import kotlinx.html.TR
import kotlinx.html.visit

// Main target: panel source section (innerHTML swap)
// Partial target: #plan-row-{id} — a <tr> inside a different table
respondHtml(createHTML().div {
    id = "resource-panel-source"
    resourcePanelSourceSection(resource)

    hxPartial(target = "#plan-row-${resource.id}", swap = "outerHTML") {
        TR(mapOf("id" to "plan-row-${resource.id}"), consumer).visit {
            planResourceRow(worldId, projectId, resource)  // renders td children
        }
    }
})
```

Do NOT put the `<tr>` at the response top level, and do NOT wrap it in a throwaway
`<table style="display:none">` (inline-style violation + needless DOM node).

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
