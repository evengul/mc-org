package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.FormEncType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span

// TODO: Nearby projects and resources
fun DIV.locationTab(project: Project) {
    classes += "project-location-tab"
    div("project-location-header") {
        h2 {
            + "Project Location"
        }
        neutralButton("Edit Location") {
            buttonBlock = {
                hxGet(Link.Worlds.world(project.worldId).project(project.id).to + "/location/edit")
                hxTarget(".location-details")
            }
        }
    }
    p("subtle") {
        + "The location of this project in your Minecraft world."
    }
    div {
        locationDetails(project.location)
    }
}

fun DIV.locationDetails(location: MinecraftLocation?) {
    classes += "location-details"
    location?.let {
        div("coordinate-details") {
            span("coordinate") {
                p {
                    + "X Coordinate"
                }
                p("coordinate-value") {
                    + it.x.toString()
                }
            }
            span("coordinate") {
                p {
                    + "Y Coordinate"
                }
                p("coordinate-value") {
                    + it.y.toString()
                }
            }
            span("coordinate") {
                p {
                    + "Z Coordinate"
                }
                p("coordinate-value") {
                    + it.z.toString()
                }
            }
        }
        span("dimension") {
            p {
                + "Dimension"
            }
            chipComponent {
                + it.dimension.toPrettyEnumName()
            }
        }
    }
}

fun DIV.editLocation(worldId: Int, projectId: Int, location: MinecraftLocation) {
    classes += "project-location-edit"
    form {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxTarget(".project-location-edit")
        hxPut(Link.Worlds.world(worldId).project(projectId).to + "/location")

        div("coordinate-details") {
            span("coordinate") {
                label {
                    htmlFor = "project-location-coordinate-x"
                    + "X Coordinate"
                    span("required-indicator") { +"*" }
                }
                input(classes = "coordinate-value") {
                    id = "project-location-coordinate-x"
                    name = "x"
                    value = location.x.toString()
                }
            }
            span("coordinate") {
                label {
                    htmlFor = "project-location-coordinate-y"
                    + "Y Coordinate"
                    span("required-indicator") { +"*" }
                }
                input(classes = "coordinate-value") {
                    id = "project-location-coordinate-y"
                    name = "y"
                    value = location.y.toString()
                }
            }
            span("coordinate") {
                label {
                    htmlFor = "project-location-coordinate-z"
                    + "Z Coordinate"
                    span("required-indicator") { +"*" }
                }
                input(classes = "coordinate-value") {
                    id = "project-location-coordinate-z"
                    name = "z"
                    value = location.z.toString()
                }
            }
        }
        span("dimension") {
            label {
                htmlFor = "project-location-dimension-select"
                + "Dimension"
                span("required-indicator") { +"*" }
            }
            select {
                id = "project-location-dimension-select"
                name = "dimension"
                Dimension.entries.forEach {
                    option {
                        value = it.name
                        selected = it == location.dimension
                        + it.toPrettyEnumName()
                    }
                }
            }
        }

        actionButton("Save Location") {
            buttonBlock = {
                id = "save-location"
            }
        }
    }
}