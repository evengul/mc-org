package app.mcorg.presentation.templated.dsl

import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.failure.userMessage
import app.mcorg.presentation.hxPartial
import kotlinx.html.FlowContent
import kotlinx.html.LI
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.visit

/**
 * Where a validation message for [field] lands, next to its input.
 *
 * [field] is the name of the request parameter the server validates, which is the input's
 * `name`. The slot needs no id: a message is placed in the slot for its field inside the form
 * that sent the request ([fieldMessages]), so two forms on one page may both have a `name`.
 */
fun FlowContent.fieldError(field: String) {
    p("form-error") {
        attributes[FIELD_SLOT] = fieldKey(field)
        attributes["aria-live"] = "polite"
    }
}

/** Marks a slot; read by [fieldMessages] and by `form-errors.js`. */
const val FIELD_SLOT = "data-error-for"

/** Marks a message with its field, inside a partial or the fallback alert. */
const val FIELD_MESSAGE = "data-field-message"

/** Marks the alert that carries messages no slot took. */
const val FIELD_FALLBACK = "data-field-fallback"

/** `items[]` and `items` are one field to a person reading the form. */
private fun fieldKey(field: String) = field.replace("[]", "")

/**
 * The body of a validation failure: one htmx partial per failed field, aimed at
 * `find [data-error-for='<field>']` from the element that sent the request, and one more
 * carrying every message as an alert.
 *
 * `form-errors.js` finishes the job in `htmx:before:swap`. A partial whose `find` matched
 * nothing (the request came from an input rather than a form, say) goes to the nearest slot for
 * its field above that element, and the alert keeps only the messages that found no slot at
 * all, or is dropped when every one did. So a field without a slot, an inline edit for
 * instance, still shows its message, and the server never needs to know the page.
 */
fun fieldMessages(errors: List<ValidationFailure>): String {
    val messages = errors
        .groupBy { fieldKey(it.parameterName) }
        .mapValues { (_, failures) -> failures.joinToString(" ") { it.userMessage() } }

    return buildString {
        messages.forEach { (field, message) ->
            append(createHTML().hxPartial(target = "find [$FIELD_SLOT='${cssString(field)}']") {
                span {
                    attributes[FIELD_MESSAGE] = field
                    +message
                }
            })
        }
        append(createHTML().hxPartial(target = "#$ALERT_CONTAINER_ID", swap = "afterbegin") {
            LI(mapOf(FIELD_FALLBACK to ""), consumer).visit {
                createAlert(
                    id = "field-messages-${System.nanoTime()}",
                    type = AlertType.ERROR,
                    title = "Check the form",
                )
                messages.forEach { (field, message) ->
                    p("alert-dialog__message") {
                        attributes[FIELD_MESSAGE] = field
                        +message
                    }
                }
            }
        })
    }
}

/** A CSS string body for an attribute selector in single quotes. */
private fun cssString(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")
