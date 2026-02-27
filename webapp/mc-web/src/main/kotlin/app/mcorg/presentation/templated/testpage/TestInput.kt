package app.mcorg.presentation.templated.testpage

import kotlinx.html.InputType
import kotlinx.html.MAIN
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.summary
import kotlinx.html.textArea

fun MAIN.testInput() {
    details {
        summary { + "Input" }
        div("input-row") {
            input(type = InputType.text, name = "textInput") {
                placeholder = "Text Input"
            }
            input(type = InputType.number, name = "numberInput") {
                placeholder = "Number Input"
            }
            input(type = InputType.email, name = "emailInput") {
                placeholder = "Email Input"
            }
            input(type = InputType.password, name = "passwordInput") {
                placeholder = "Password Input"
            }
            input(type = InputType.text, name = "disabledInput") {
                disabled = true
                placeholder = "Disabled Input"
            }
        }
        div("input-row") {
            textArea {
                placeholder = "Text Area"
            }
            textArea {
                disabled = true
                placeholder = "Disabled Text Area"
            }
        }
        div("input-row") {
            input(type = InputType.checkBox, name = "checkBox")
            input(type = InputType.checkBox) {
                disabled = true
            }
        }
        div("input-row") {
            input(type = InputType.radio, name = "radioInput")
            input(type = InputType.radio, name = "radioInput") {
                disabled = true
            }
        }
        div("input-row") {
            select {
                option {
                    value = "option1"
                    + "Option 1"
                }
                option {
                    value = "option2"
                    + "Option 2"
                }
                option {
                    value = "option3"
                    + "Option 3"
                }
            }
        }
    }
}