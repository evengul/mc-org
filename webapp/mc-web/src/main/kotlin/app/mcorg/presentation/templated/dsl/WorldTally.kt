package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.world.WorldProjectTally
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.span

/**
 * A world's projects as counts a player can act on, in the Field Log's own vocabulary.
 * This replaced a completion bar (MCO-468): a world is never finished — you keep adding
 * projects — so the percentage only ever measured how recently you had added one, and it
 * counted shelved work in its denominator. Zero-valued parts are dropped; "done" always
 * shows, because it is what the total is read against.
 */
fun FlowContent.worldTally(tally: WorldProjectTally, compact: Boolean = false) {
    val modifiers = if (compact) " world-tally--compact" else ""
    if (tally.isEmpty) {
        // "0 done of 0" is not a fact worth printing.
        div("world-tally$modifiers world-tally--empty") { +"No projects yet" }
        return
    }
    val parts = buildList {
        if (tally.active > 0) add(tally.active to "in flight")
        if (tally.pending > 0) add(tally.pending to "queued")
        if (tally.paused > 0) add(tally.paused to "paused")
        add(tally.done to "done")
    }
    // The spaces are text nodes, not decoration: a flex container drops whitespace-only
    // runs when it lays out, so they cost nothing visually (the gap does the spacing) but
    // keep the line readable to a screen reader and to anyone who copies it.
    div("world-tally$modifiers") {
        parts.forEachIndexed { index, (count, caption) ->
            if (index > 0) {
                +" "
                span("world-tally__sep") {
                    attributes["aria-hidden"] = "true"
                    +"·"
                }
                +" "
            }
            span("world-tally__part") {
                span("world-tally__count") { +count.toString() }
                +" "
                span("world-tally__label") { +caption }
            }
        }
        +" "
        span("world-tally__total") { +"of ${tally.total}" }
    }
}
