package app.mcorg.presentation.templated.testpage

import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.summary

fun MAIN.testChips() {
    details {
        summary { + "Chips" }
        div("chip-row") {
            ChipVariant.entries.forEachIndexed { i, it ->
                chipComponent {
                    variant = it
                    text = "Chip - ${it.name}"
                    icon = Icons.Dimensions.OVERWORLD
                    onClick = if (i == 0) {
                        "alert('You clicked the first chip!')"
                    } else {
                        null
                    }
                }
            }
        }
    }
}