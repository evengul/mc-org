package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul

fun DIV.dependenciesTab(dependencies: List<ProjectDependency>, dependents: List<ProjectDependency>) {
    classes += "dependencies-tab"
    div("project-dependencies") {
        div("project-dependencies-header") {
            div("project-dependencies-header-title") {
                h2 {
                    +"Project Dependencies"
                }
                p("subtle") {
                    +"Projects that this project depends on"
                }
            }
            actionButton("Add Dependency")
        }
        if (dependencies.isEmpty()) {
            p("subtle") {
                + "This project doesn't depend on any other projects yet. Add dependencies to help track project relationships."
            }
        } else {
            ul("dependency-list") {
                dependencies.forEach { dependency ->
                    li {
                        p {
                            + dependency.dependencyName
                        }
                        span("dependency-item-actions") {
                            chipComponent {
                                + dependency.dependencyStage.toPrettyEnumName()
                            }
                            iconButton(Icons.DELETE, iconSize = IconSize.SMALL)
                        }
                    }
                }
            }
            div("project-dependency-footer") {
                p("subtle") {
                    + "${dependencies.size} ${if (dependencies.size == 1) "dependency" else "dependencies"}"
                }
                chipComponent {
                    if (dependencies.all { it.dependencyStage == ProjectStage.COMPLETED }) {
                        color = ChipColor.SUCCESS
                        + "All dependencies completed"
                    } else {
                        color = ChipColor.NEUTRAL
                        + "Some dependencies not completed"
                    }
                }
            }
        }
    }
    div("project-dependents") {
        h2 {
            + "Dependent Projects"
        }
        p("subtle") {
            + "Projects that depend on this project"
        }
        if (dependents.isEmpty()) {
            p("subtle") {
                + "No other projects depend on this project yet. When other projects add this project as a dependency, they will appear here."
            }
        } else {
            ul("dependent-list") {
                dependents.forEach { dependent ->
                    li {
                        p {
                            + dependent.dependentName
                        }
                        chipComponent {
                            + dependent.dependentStage.toPrettyEnumName()
                        }
                    }
                }
            }
            div("dependent-summary") {
                p("subtle") {
                    + "${dependents.size} projects depend on this project"
                }
            }
        }
    }
}
