package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Chip-sized labels. `toPrettyEnumName()` yields "Technical Understanding Recommended", which is
 * wider than the three game-stage options put together and wraps the row into a mess.
 *
 * Laying these out side by side exposes something the dropdown hid: this enum is two axes in one —
 * how far into the game you are, and how much redstone knowledge it takes. They are not mutually
 * exclusive in reality, so a single-choice control cannot express "end game and technical".
 */
private fun IdeaDifficulty.shortLabel(): String = when (this) {
    IdeaDifficulty.START_OF_GAME -> "Start of game"
    IdeaDifficulty.MID_GAME -> "Mid game"
    IdeaDifficulty.END_GAME -> "End game"
    IdeaDifficulty.TECHNICAL_UNDERSTANDING_RECOMMENDED -> "Technical (recommended)"
    IdeaDifficulty.TECHNICAL_UNDERSTANDING_REQUIRED -> "Technical (required)"
}

fun FlowContent.draftBasicInfoFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())

    div {
        label {
            htmlFor = "draft-name"
            +"Name"
            span("required-indicator") { +"*" }
        }
        input(classes = "form-control") {
            id = "draft-name"
            name = "name"
            type = InputType.text
            required = true
            minLength = "3"
            maxLength = "100"
            placeholder = "e.g., High-Speed Sugar Cane Farm"
            value = data.name ?: ""
        }
        p("form-error") { id = "error-name" }
    }

    div {
        label {
            htmlFor = "draft-description"
            +"Description"
        }
        // Optional, and no minimum length: a private design is allowed to be a name and a
        // category. Requirements for putting one on the hub come later, and separately.
        textArea(classes = "form-control") {
            id = "draft-description"
            name = "description"
            rows = "4"
            maxLength = "5000"
            placeholder = "How it works, what makes it good, anything you'd want to remember later…"
            +(data.description ?: "")
        }
        p("form-error") { id = "error-description" }
    }

    div {
        label {
            +"Difficulty"
            span("required-indicator") { +"*" }
        }
        // Radios rather than a select: five options is few enough to show at once, it matches the
        // category picker directly below, and it saves a click on a field that is always answered.
        div("category-select") {
            IdeaDifficulty.entries.forEach { difficulty ->
                label("filter-radio-label") {
                    input(type = InputType.radio) {
                        classes += "category-radio"
                        name = "difficulty"
                        value = difficulty.name
                        checked = (data.difficulty == difficulty) ||
                                (data.difficulty == null && difficulty == IdeaDifficulty.MID_GAME)
                        required = true
                    }
                    +difficulty.shortLabel()
                }
            }
        }
        p("form-error") { id = "error-difficulty" }
    }
}
