package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.span

// TODO: Nearby projects and resources
fun DIV.locationTab(project: Project) {
    classes += "project-location-tab"
    div("project-location-header") {
        h2 {
            + "Project Location"
        }
        neutralButton("Edit Location")
    }
    p("subtle") {
        "The location of this project in your Minecraft world."
    }
    project.location?.let {
        div("location-details") {
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
}