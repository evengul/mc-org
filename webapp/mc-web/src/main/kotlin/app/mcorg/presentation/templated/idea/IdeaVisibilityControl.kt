package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.Link
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML

/**
 * The publish-to-hub affordance on an idea's detail page (MCO-291).
 *
 * Only the owner (or a superadmin) sees anything here: for everyone else an idea's visibility is
 * not their business. Publishing additionally needs the publishing role, so a creator without it
 * sees why the button is absent rather than a button that 403s.
 */
fun FlowContent.ideaVisibilityControl(idea: Idea, user: TokenProfile) {
    div("idea-visibility") {
        id = VISIBILITY_ID
        visibilityContent(idea, user)
    }
}

/** Same control as an HTMX fragment, for the response to a successful publish. */
fun ideaVisibilityControlFragment(idea: Idea, user: TokenProfile): String =
    createHTML().div("idea-visibility") {
        id = VISIBILITY_ID
        visibilityContent(idea, user)
    }

private const val VISIBILITY_ID = "idea-visibility"

private fun DIV.visibilityContent(idea: Idea, user: TokenProfile) {
    val isOwner = idea.createdBy == user.id || user.isSuperAdmin
    if (!isOwner) return

    when {
        idea.visibility == IdeaVisibility.PUBLIC ->
            span("idea-visibility__state") { +"On the community hub" }

        user.isIdeaCreator -> button(classes = "btn btn--secondary") {
            type = ButtonType.button
            hxPatch(Link.Ideas.single(idea.id) + "/public")
            hxTarget("#$VISIBILITY_ID")
            hxSwap("outerHTML")
            +"Publish to hub"
        }

        else -> span("idea-visibility__state") {
            +"Private — you don't have permission to publish to the hub"
        }
    }
}
