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
            span("required-indicator") { +"*" }
        }
        textArea(classes = "form-control") {
            id = "draft-description"
            name = "description"
            required = true
            rows = "6"
            minLength = "20"
            maxLength = "5000"
            placeholder = "Describe your contraption, how it works, and what makes it unique..."
            +(data.description ?: "")
        }
        p("form-error") { id = "error-description" }
    }

    div {
        label {
            htmlFor = "draft-difficulty"
            +"Difficulty"
            span("required-indicator") { +"*" }
        }
        select(classes = "form-control") {
            id = "draft-difficulty"
            name = "difficulty"
            required = true
            IdeaDifficulty.entries.forEach { difficulty ->
                option {
                    value = difficulty.name
                    selected = (data.difficulty == difficulty) || (data.difficulty == null && difficulty == IdeaDifficulty.MID_GAME)
                    +difficulty.toPrettyEnumName()
                }
            }
        }
        p("form-error") { id = "error-difficulty" }
    }
}
