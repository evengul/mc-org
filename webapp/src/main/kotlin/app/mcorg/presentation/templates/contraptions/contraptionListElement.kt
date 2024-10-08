package app.mcorg.presentation.templates.contraptions

import app.mcorg.domain.Contraption
import app.mcorg.domain.toVersionString
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templates.world.presentable
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createContraptionListElement(contraption: Contraption) = createHTML().li {
    contraptionListElement(contraption)
}

fun LI.contraptionListElement(contraption: Contraption) {
    id = "contraption-${contraption.id}"
    span {
        classes = setOf("contraptions-title-delete")
        h2 {
            + contraption.name
        }
        button {
            classes = setOf("icon-row button button-icon icon-small icon-delete-small")
            hxDelete("/app/contraptions/${contraption.id}")
            hxTarget("#contraption-${contraption.id}")
            hxSwap("outerHTML")
            hxConfirm("Are you sure this contraption should be deleted?")
        }
    }
    p {
        + "Game: ${contraption.worksInGame.presentable()}"
    }
    p {
        + "Created by: ${contraption.authors.joinToString(", ")}"
    }
    p {
        + "Works in: ${contraption.worksInVersion.lowerBound.toVersionString()}${contraption.worksInVersion.upperBound?.toVersionString()?.let { "-$it" } ?: "+"}"
    }
    if (contraption.description.isNotBlank()) {
        p {
            + contraption.description
        }
    }
    if (contraption.schematicUrl?.isNotBlank() == true) {
        a(href = contraption.schematicUrl, target = "_blank") {
            + "Schematic Download"
        }
    }
    if (contraption.worldDownloadUrl?.isNotBlank() == true) {
        a(href = contraption.worldDownloadUrl, target = "_blank") {
            + "World Download"
        }
    }
}