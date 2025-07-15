package app.mcorg.presentation.templated.testpage

import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.summary

fun MAIN.testFonts() {
    details {
        summary { + "Fonts" }
        div("font-row") {
            h1 {
                + "Heading 1"
            }
            h2 {
                + "Heading 2"
            }
            h3 {
                + "Heading 3"
            }
            p {
                + "This is a paragraph with the default font style. It should be clear and readable, demonstrating the standard text appearance."
            }
            label {
                + "This is a label for form elements, styled to be distinct and easily identifiable."
            }
        }
    }
}