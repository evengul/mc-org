package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaProductionMode
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul

/**
 * What an idea produces (MCO-412), read from its own tables rather than category data.
 *
 * A mode name only appears when there is more than one. An author who never mentioned modes gets
 * the implicit "Default", and printing that word back at them would name a choice they did not
 * make — the list is just items and rates until the design actually has alternatives.
 */
fun FlowContent.ideaProductionModes(modes: List<IdeaProductionMode>) {
    val populated = modes.filter { it.rates.isNotEmpty() }
    if (populated.isEmpty()) return

    val named = populated.size > 1

    section("idea-productions") {
        h2("idea-detail__section-title") { +"Produces" }
        populated.forEach { mode ->
            div("idea-productions__mode") {
                if (named) {
                    span("idea-productions__mode-name") { +mode.name }
                }
                ul("idea-productions__rates") {
                    // Measured output first; an unmeasured item is still output and still listed,
                    // it just cannot claim a number.
                    mode.rates.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int?>> { it.value ?: -1 }.thenBy { it.key })
                        .forEach { (itemId, rate) ->
                            li("idea-productions__rate") {
                                if (rate != null) {
                                    span("idea-productions__quantity") { +"%,d".format(rate) }
                                    +" / hour "
                                }
                                span("idea-productions__item") { +itemId.removePrefix("minecraft:").replace('_', ' ') }
                                if (rate == null) {
                                    span("idea-productions__unmeasured") { +" — rate unmeasured" }
                                }
                            }
                        }
                }
            }
        }
    }
}
