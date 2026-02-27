package app.mcorg.presentation.templated.testpage

import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.summary

fun MAIN.testSizes() {
    details {
        id = "test-sizes"
        summary { + "Sizes" }
        div("row") {
            div("size-column") {
                id = "test-sizes-spacing"
                p { + "Spacing" }
                div {
                    id = "test-size-xxs"
                    + "XXS"
                }
                div {
                    id = "test-size-xs"
                    + "XS"
                }
                div {
                    id = "test-size-sm"
                    + "SM"
                }
                div {
                    id = "test-size-md"
                    + "MD"
                }
                div {
                    id = "test-size-lg"
                    + "L"
                }
                div {
                    id = "test-size-xl"
                    + "XL"
                }
            }
            div("size-column") {
                id = "test-sizes-borders"
                p { + "Borders" }
                div {
                    id = "test-size-border-sm"
                    + "SM"
                }
                div {
                    id = "test-size-border-md"
                    + "MD"
                }
                div {
                    id = "test-size-border-lg"
                    + "LG"
                }
                div {
                    id = "test-size-border-full"
                    + "Full"
                }
            }
        }
    }
}