package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectStageChange
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun DIV.stagesTab(stageChanges: List<ProjectStageChange>) {
    classes += "stages-tab"
    h2 {
        + "Project Stages"
    }
    p("subtle") {
        + "Track the progress of your project through different stages"
    }
    ul("stages-list") {
        ProjectStage.entries.sortedBy { it.order }.forEach { stage ->
            val change = stageChanges.find { it.stage == stage }
            li("stage-item") {
                if (change == null) classes += "upcoming"

                // TODO(ICONS): Use a more appropriate icon for each stage
                span("stage-icon") {
                    iconComponent(Icons.CHECK, color = IconColor.ON_BACKGROUND)
                }
                + stage.toPrettyEnumName()
                if (change != null) {
                    p("subtle") {
                        + "Entered on: ${change.enteredOn.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm"))}"
                    }
                    if (change.relatedTasks.isEmpty()) {
                        p("subtle") {
                            + "No tasks for this stage."
                        }
                    } else {
                        ul("related-tasks") {
                            change.relatedTasks.forEach { task ->
                                li("related-task") {
                                    + task
                                }
                            }
                        }
                    }
                } else {
                    p("subtle") {
                        + "Upcoming stage"
                    }
                }
            }
        }
    }
}