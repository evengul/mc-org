package app.mcorg.presentation.templates.contraptions

import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*

fun MAIN.createContraptionDialog() {
    dialog {
        id = "create-contraption-dialog"
        form {
            id = "create-contraption-form"
            method = FormMethod.post
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxPost("/app/contraptions")
            hxTarget("#contraptions-list")
            hxSwap("afterbegin")
            label {
                htmlFor = "create-contraption-name-input"
                + "Name"
            }
            input {
                id = "create-contraption-name-input"
                type = InputType.text
                required = true
                minLength = "2"
                name = "name"
            }
            label {
                htmlFor = "create-contraption-description-input"
                + "Description"
            }
            textArea {
                id = "create-contraption-description-input"
                name = "description"
                rows = "3"
            }
            label {
                htmlFor = "create-contraption-author-input"
                + "Author (Separate multiple with ;)"
            }
            input {
                id = "create-contraption-author-input"
                type = InputType.text
                required = true
                name = "authors"
            }
            label {
                htmlFor = "create-contraption-game-type-input"
                + "Game type"
            }
            select {
                required = true
                id = "create-contraption-game-type-input"
                name = "game-type"
                option {
                    selected = true
                    value = "JAVA"
                    + "Java"
                }
                option {
                    value = "BEDROCK"
                    + "Bedrock"
                }
            }
            label {
                htmlFor = "create-contraption-version-input"
                + "Compatible version(s) (1.xx.x-1.xx.x)"
            }
            input {
                id = "create-contraption-version-input"
                type = InputType.text
                required = true
                name = "version"
            }
            label {
                htmlFor = "create-contraption-schematic-url"
                + "Schematic URL"
            }
            input {
                id = "create-contraption-schematic-url"
                type = InputType.url
                name = "schematicUrl"
            }
            label {
                htmlFor = "create-contraption-world-download-url"
                + "World download URL"
            }
            input {
                id = "create-contraption-world-download-url"
                type = InputType.url
                name = "worldDownloadUrl"
            }
            span {
                classes = setOf("button-row")
                button {
                    onClick = "hideDialog('create-contraption-dialog')"
                    classes = setOf("button-secondary")
                    type = ButtonType.button
                    + "Cancel"
                }
                button {
                    type = ButtonType.submit
                    + "Create contraption"
                }
            }
        }
    }
}