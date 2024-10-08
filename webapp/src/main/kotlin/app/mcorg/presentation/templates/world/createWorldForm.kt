package app.mcorg.presentation.templates.world

import kotlinx.html.*

fun FORM.createWorld(versions: List<String>, playerIsTechnical: Boolean, canCancel: Boolean = true) {
    method = FormMethod.post
    encType = FormEncType.applicationXWwwFormUrlEncoded
    id = "create-world-form"
    label {
        htmlFor = "add-world-name-input"
        + "Name of your world"
    }
    input {
        id = "add-world-name-input"
        name = "worldName"
        type = InputType.text
        required = true
        minLength = "3"
        maxLength = "100"
    }
    label {
        htmlFor = "add-world-game-type-input"
        + "Game type"
    }
    select {
        id = "add-world-game-type-input"
        name = "gameType"
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
        htmlFor = "add-world-version-input"
        + "Version"
    }
    select {
        id = "add-world-version-input"
        name = "version"
        for (version in versions) {
            option {
                value = version
                + version
            }
        }
    }
    label {
        htmlFor = "add-world-is-technical-input"
        + "Technical world (more refined project creation)"
    }
    input {
        type = InputType.checkBox
        name = "isTechnical"
        checked = playerIsTechnical
    }
    span {
        classes = setOf("button-row")
        if (canCancel) {
            button {
                onClick = "hideDialog('create-world-dialog')"
                classes = setOf("button-secondary")
                type = ButtonType.button
                + "Cancel"
            }
        }
        button {
            id = "add-world-submit-button"
            type = ButtonType.submit
            + "Create new world"
        }
    }
}