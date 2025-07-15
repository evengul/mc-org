package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.progress.progressComponent
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary

fun MAIN.testProgress() {
    details {
        summary { + "Progress" }
        div("progress-row") {
            progressComponent {
                value = 50.0
                max = 100.0
            }
            progressComponent {
                value = 75.0
                max = 100.0
            }
            progressComponent {
                value = 25.0
                max = 100.0
            }
            progressComponent {
                value = 0.0
                max = 100.0
            }
            progressComponent {
                value = 100.0
                max = 100.0
            }
            progressComponent {
                value = 0.0
                max = 100.0
            }
        }
    }
}