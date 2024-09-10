package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun addLitematicaTasks(worldId: Int, projectId: Int): String = subPageTemplate(
    title = "Litematica Upload",
    backLink = "/app/worlds/$worldId/projects/$projectId",
) {
    form {
        encType = FormEncType.multipartFormData
        method = FormMethod.post
        label {
            htmlFor = "tasks-add-litematica-file-input"
            + "Select litematica material list"
        }
        input {
            id = "tasks-add-litematica-file-input"
            type = InputType.file
            name = "file"
        }
        button {
            type = ButtonType.submit
            + "Upload litematica material list"
        }
    }
}