package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

/**
 * Inline editors for the editable project-detail header fields: name, state and
 * location. Each field is a self-contained `div` with a stable id; the view shows
 * the value plus an edit affordance (admins only) that swaps the wrapper's
 * outerHTML for an edit form, and saving/cancelling swaps it back.
 *
 * Three fields, one pattern. GET `…/meta/{field}?mode=edit` returns the edit form;
 * PATCH `…/meta/{field}` saves and returns the view.
 */

private fun nameFieldId(projectId: Int) = "project-meta-name-$projectId"
private fun stateFieldId(projectId: Int) = "project-meta-state-$projectId"
private fun locationFieldId(projectId: Int) = "project-meta-location-$projectId"

private fun metaBase(project: Project) = "/worlds/${project.worldId}/projects/${project.id}/meta"

private fun FlowContent.editTrigger(getUrl: String, targetId: String, label: String) {
    button(classes = "btn btn--ghost btn--sm project-meta-field__edit") {
        type = ButtonType.button
        hxGet(getUrl)
        hxTarget("#$targetId")
        hxSwap("outerHTML")
        attributes["aria-label"] = label
        +"Edit"
    }
}

private fun FlowContent.editActions(cancelViewUrl: String, targetId: String) {
    div("project-meta-edit__actions") {
        button(classes = "btn btn--primary btn--sm") {
            type = ButtonType.submit
            +"Save"
        }
        button(classes = "btn btn--ghost btn--sm") {
            type = ButtonType.button
            hxGet(cancelViewUrl)
            hxTarget("#$targetId")
            hxSwap("outerHTML")
            +"Cancel"
        }
    }
}

// ---------------------------------------------------------------------------
// Name
// ---------------------------------------------------------------------------

fun FlowContent.projectNameField(project: Project, isAdmin: Boolean) {
    div("project-meta-field project-meta-field--name") {
        id = nameFieldId(project.id)
        nameViewInner(project, isAdmin)
    }
}

fun projectNameViewFragment(project: Project, isAdmin: Boolean): String =
    createHTML().div("project-meta-field project-meta-field--name") {
        id = nameFieldId(project.id)
        nameViewInner(project, isAdmin)
    }

fun projectNameEditFragment(project: Project): String =
    createHTML().div("project-meta-field project-meta-field--name") {
        id = nameFieldId(project.id)
        nameEditInner(project)
    }

private fun DIV.nameViewInner(project: Project, isAdmin: Boolean) {
    h1("project-detail__name") { +project.name }
    if (isAdmin) editTrigger("${metaBase(project)}/name?mode=edit", nameFieldId(project.id), "Edit project name")
}

private fun DIV.nameEditInner(project: Project) {
    form(classes = "project-meta-edit") {
        hxPatch("${metaBase(project)}/name")
        hxTarget("#${nameFieldId(project.id)}")
        hxSwap("outerHTML")
        input(type = InputType.text, name = "name", classes = "project-meta-edit__input project-meta-edit__input--name") {
            value = project.name
            attributes["maxlength"] = "100"
            attributes["minlength"] = "3"
            attributes["required"] = "required"
            attributes["aria-label"] = "Project name"
            attributes["autofocus"] = "autofocus"
        }
        editActions("${metaBase(project)}/name", nameFieldId(project.id))
    }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

fun FlowContent.projectStateField(project: Project, isAdmin: Boolean) {
    div("project-meta-field project-meta-field--state") {
        id = stateFieldId(project.id)
        stateViewInner(project, isAdmin)
    }
}

fun projectStateViewFragment(project: Project, isAdmin: Boolean): String =
    createHTML().div("project-meta-field project-meta-field--state") {
        id = stateFieldId(project.id)
        stateViewInner(project, isAdmin)
    }

fun projectStateEditFragment(project: Project): String =
    createHTML().div("project-meta-field project-meta-field--state") {
        id = stateFieldId(project.id)
        stateEditInner(project)
    }

private fun DIV.stateViewInner(project: Project, isAdmin: Boolean) {
    span("badge ${project.state.badgeModifier}") { +project.state.label }
    if (isAdmin) editTrigger("${metaBase(project)}/state?mode=edit", stateFieldId(project.id), "Change project state")
}

private fun DIV.stateEditInner(project: Project) {
    form(classes = "project-meta-edit") {
        hxPatch("${metaBase(project)}/state")
        hxTarget("#${stateFieldId(project.id)}")
        hxSwap("outerHTML")
        select(classes = "project-meta-edit__select") {
            name = "state"
            attributes["aria-label"] = "New project state"
            project.state.allowedTransitions().forEach { target ->
                option {
                    value = target.name
                    +target.label
                }
            }
        }
        editActions("${metaBase(project)}/state", stateFieldId(project.id))
    }
}

// ---------------------------------------------------------------------------
// Location (X/Z — "where is the build")
// ---------------------------------------------------------------------------

fun FlowContent.projectLocationField(project: Project, isAdmin: Boolean) {
    div("project-meta-field project-meta-field--location") {
        id = locationFieldId(project.id)
        locationViewInner(project, isAdmin)
    }
}

fun projectLocationViewFragment(project: Project, isAdmin: Boolean): String =
    createHTML().div("project-meta-field project-meta-field--location") {
        id = locationFieldId(project.id)
        locationViewInner(project, isAdmin)
    }

fun projectLocationEditFragment(project: Project): String =
    createHTML().div("project-meta-field project-meta-field--location") {
        id = locationFieldId(project.id)
        locationEditInner(project)
    }

private fun DIV.locationViewInner(project: Project, isAdmin: Boolean) {
    val loc = project.location
    if (loc != null) {
        span("project-detail__location") { +"X: ${loc.x}, Z: ${loc.z}" }
        if (isAdmin) editTrigger("${metaBase(project)}/location?mode=edit", locationFieldId(project.id), "Edit location")
    } else {
        span("project-detail__location project-detail__location--unset") { +"No location set" }
        if (isAdmin) editTrigger("${metaBase(project)}/location?mode=edit", locationFieldId(project.id), "Set location")
    }
}

private fun DIV.locationEditInner(project: Project) {
    val loc = project.location
    form(classes = "project-meta-edit project-meta-edit--location") {
        hxPatch("${metaBase(project)}/location")
        hxTarget("#${locationFieldId(project.id)}")
        hxSwap("outerHTML")
        input(type = InputType.number, name = "x", classes = "project-meta-edit__input project-meta-edit__input--coord") {
            value = loc?.x?.toString() ?: ""
            attributes["required"] = "required"
            attributes["aria-label"] = "X coordinate"
            attributes["placeholder"] = "X"
        }
        input(type = InputType.number, name = "z", classes = "project-meta-edit__input project-meta-edit__input--coord") {
            value = loc?.z?.toString() ?: ""
            attributes["required"] = "required"
            attributes["aria-label"] = "Z coordinate"
            attributes["placeholder"] = "Z"
        }
        editActions("${metaBase(project)}/location", locationFieldId(project.id))
    }
}
