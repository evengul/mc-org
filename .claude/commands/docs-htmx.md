# HTMX Patterns Reference

HTMX helper functions and interaction patterns for MC-ORG.

---

## HTMX Helper Functions (hx.kt)

```kotlin
// HTTP methods
fun HTMLTag.hxGet(value: String)
fun HTMLTag.hxPost(value: String)
fun HTMLTag.hxPut(value: String)
fun HTMLTag.hxPatch(value: String)
fun HTMLTag.hxDelete(value: String)

// Delete with confirmation modal (custom MC-ORG component)
fun HTMLTag.hxDeleteWithConfirm(
    url: String,
    title: String,
    description: String,
    warning: String,
    confirmText: String? = null  // if set, user must type this to confirm
)

// Targeting and swapping
fun HTMLTag.hxTarget(value: String)    // CSS selector for swap target
fun HTMLTag.hxSwap(value: String)      // swap strategy (see table below)

// Additional attributes
fun HTMLTag.hxConfirm(value: String)   // simple confirm dialog
fun HTMLTag.hxTrigger(value: String)   // e.g., "load", "click", "change"
fun HTMLTag.hxPushUrl(value: String)   // update browser URL

// Include additional form data
fun HTMLTag.hxInclude(value: String)   // CSS selector for extra inputs to include

// Out-of-band swaps (update multiple elements in one response)
fun HTMLTag.hxOutOfBands(locator: String)  // "true" or element spec

// Error targeting
fun HTMLTag.hxErrorTarget(target: String)
fun HTMLTag.hxErrorTarget(target: String, errorCode: String)

// Extensions
fun HTMLTag.hxExtension(value: String)
```

## HTMX Swap Strategies

| Value | Effect |
|-------|--------|
| `innerHTML` | Replace inner content (default) |
| `outerHTML` | Replace entire element |
| `beforeend` | Append to end of target |
| `afterbegin` | Prepend to beginning of target |
| `delete` | Remove target element |

## Response Helpers (htmxResponseUtils.kt)

```kotlin
// HTMX redirect — sets HX-Redirect header (no full page reload)
suspend fun ApplicationCall.clientRedirect(path: String)

// Error responses — sets HX-ReTarget and HX-ReSwap for error display
suspend fun ApplicationCall.respondBadRequest(
    errorHtml: String = "An error occurred",
    target: String = "#error-message",
    swap: String = "innerHTML"
)
suspend fun ApplicationCall.respondNotFound(errorHtml: String = "Not found", ...)
```

---

## Common Patterns

### Form submission

```kotlin
// Template
form {
    id = "create-project-form"
    hxPost("/app/worlds/${worldId}/projects")
    hxTarget("#create-project-form")

    input(classes = "form-control") { name = "name"; placeholder = "Project name" }
    button(classes = "btn btn--action") { type = ButtonType.submit; +"Create" }
}

// Handler response (replaces the form)
respondHtml(createHTML().div {
    id = "create-project-form"
    div("notice notice--success") { +"Project created successfully!" }
})
```

### Update action (button trigger)

```kotlin
// Template
button(classes = "btn btn--action") {
    hxPut("/app/worlds/${worldId}/projects/${projectId}/stage")
    hxTarget("#project-stage")
    +"Advance Stage"
}

// Handler response
respondHtml(createHTML().div {
    id = "project-stage"
    span("badge badge--success") { +"Building" }
})
```

### Delete with confirmation

```kotlin
// Template
button(classes = "btn btn--danger") {
    hxDeleteWithConfirm(
        url = "/app/worlds/${worldId}/projects/${projectId}",
        title = "Delete Project",
        description = "Are you sure you want to delete this project?",
        warning = "This will also delete all tasks and cannot be undone.",
        confirmText = "DELETE"  // user must type this
    )
    +"Delete Project"
}

// Handler response (after deletion)
respondHtml(createHTML().div {
    id = "project-card"  // replaced/removed element
    div("notice notice--success") { +"Project deleted." }
})
```

### Dynamic content load on page load

```kotlin
div {
    id = "task-list"
    hxGet("/app/worlds/${worldId}/projects/${projectId}/tasks")
    hxTrigger("load")
    +"Loading tasks..."
}
```

### Out-of-band swap (update multiple elements)

```kotlin
// Response updates both #project-list (main) and #notification-count (OOB)
respondHtml(createHTML().div {
    id = "project-list"
    projectListContent(projects)

    span {
        id = "notification-count"
        hxOutOfBands("true")
        +"${notificationCount}"
    }
})
```

### Include extra inputs in request

```kotlin
button(classes = "btn btn--action") {
    hxPost("/app/worlds/${worldId}/tasks")
    hxTarget("#task-list")
    hxInclude("#task-form-inputs")  // include inputs from another element
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
        hxGet("/app/worlds/${worldId}/projects/${projectId}/edit/description")
        hxTarget("#project-description")
        +"Edit"
    }
}

// Handler returns edit form (replaces #project-description)
respondHtml(createHTML().div {
    id = "project-description"
    form {
        hxPut("/app/worlds/${worldId}/projects/${projectId}/description")
        hxTarget("#project-description")
        textarea(classes = "form-control") { name = "description"; +project.description }
        div("cluster cluster--sm") {
            button(classes = "btn btn--action") { +"Save" }
            button(classes = "btn btn--neutral") {
                hxGet("/app/worlds/${worldId}/projects/${projectId}")
                hxTarget("#project-description")
                +"Cancel"
            }
        }
    }
})
```

---

## Rules

- **GET endpoints** return full pages via `createPage(user, "Title") { ... }`
- **PUT/PATCH/POST/DELETE endpoints** return HTML fragments (NOT full pages)
- Response element `id` must match `hxTarget` selector
- Always specify `hxTarget` — never rely on defaults
- Use semantic HTTP: GET=read, POST=create, PUT=replace, PATCH=partial, DELETE=remove
