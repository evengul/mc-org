package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.pipeline.resources.FarmAsks
import app.mcorg.pipeline.resources.ItemAsk
import app.mcorg.pipeline.resources.ModeAsk
import app.mcorg.pipeline.resources.PlanAsk
import app.mcorg.presentation.templated.dsl.Link
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span

/**
 * What a farm's page says to set it to (MCO-605, design frame 2a·C).
 *
 * A farm used to say what it produces and never who was asking it for what, so "what do I set it
 * to next" had to be read off every plan in the world. This answers it on the farm: each mode the
 * plans ask for, longest first, and how much each plan takes.
 *
 * Nothing is drawn when no plan asks anything (decided 2026-10-13): the rates are already in the
 * Produces panel, and a section that says "nobody needs this" is not worth its space.
 *
 * A farm without modes is one run, so it gets one headline and no list of modes; a list of one
 * mode would repeat the headline.
 */
internal fun FlowContent.farmSetItTo(worldId: Int, worldName: String, asks: FarmAsks) {
    if (asks.isEmpty) return

    div("set-it-to") {
        div("set-it-to__card") {
            div("set-it-to__head") {
                span("section-label") { +"Set it to · $worldName" }
                span("set-it-to__note") { +"Longest first. Estimates from your measured rates; supply isn't capped." }
            }
            if (asks.farmModeCount == 0) {
                singleRun(worldId, asks.asked.flatMap { it.items })
            } else {
                modeHeadlines(worldId, asks.asked)
            }
        }
        if (asks.farmModeCount > 0) everyMode(asks)
    }
}

/** A farm without modes: how long it runs, what sets that, and each line with its plans. */
private fun FlowContent.singleRun(worldId: Int, items: List<ItemAsk>) {
    val setBy = items.filter { it.hoursLeft != null }.maxByOrNull { it.hoursLeft!! }
    p("set-it-to__headline") {
        if (setBy != null) {
            span("set-it-to__mode") { +"Runs" }
            span("set-it-to__time") { +runningTime(setBy.hoursLeft!!) }
            span("set-it-to__what") {
                +"set by ${splitKind(setBy.itemName).first}, ${"%,d".format(setBy.left)} at ${"%,d".format(setBy.ratePerHour)}/hr"
            }
        } else {
            span("set-it-to__mode") { +"No time" }
            span("set-it-to__what") { +"no item it makes for these plans has a measured rate" }
        }
    }
    div("set-it-to__lines") {
        items.sortedByDescending { it.hoursLeft ?: -1.0 }.forEach { item ->
            div("set-it-to__line") {
                span("set-it-to__item") { +"${splitKind(item.itemName).first} ${"%,d".format(item.left)}" }
                span("set-it-to__plans") { planLinks(worldId, item.asks) }
            }
        }
    }
}

/** One headline per mode that takes real time, longest first; then the modes that take seconds. */
private fun FlowContent.modeHeadlines(worldId: Int, modes: List<ModeAsk>) {
    val (tokens, timed) = modes.partition { it.isToken }
    timed.sortedByDescending { it.hoursLeft ?: -1.0 }.forEach { mode ->
        val setBy = mode.setBy ?: mode.items.first()
        p("set-it-to__headline") {
            span("set-it-to__mode") { +(mode.modeName ?: "") }
            span("set-it-to__time") { +(mode.hoursLeft?.let(::runningTime) ?: "no time") }
            span("set-it-to__what") { +"→ ${"%,d".format(setBy.left)} ${splitKind(setBy.itemName).first}" }
        }
        p("set-it-to__plans") { planLinks(worldId, setBy.asks) }
        mode.items.filterNot { it === setBy }.forEach { other ->
            p("set-it-to__also") {
                +"also ${"%,d".format(other.left)} ${splitKind(other.itemName).first} (${planCounts(other.asks)})"
            }
        }
    }
    if (tokens.isNotEmpty()) {
        div("set-it-to__tokens") {
            p("set-it-to__tokens-head") {
                val names = tokens.map { (it.modeName ?: "").removeSuffix(" Mode") }
                val lead = if (timed.isEmpty()) "Under a minute" else "Then under a minute"
                +if (tokens.size == 1) "$lead in ${tokens.single().modeName}" else "$lead each in ${joinNames(names)} Mode"
            }
            p("set-it-to__tokens-items") {
                +tokens.flatMap { it.items }.joinToString(" · ") { item ->
                    "${splitKind(item.itemName).first} ${"%,d".format(item.left)} (${planCounts(item.asks)})"
                }
            }
        }
    }
}

/** "Storage System YAMS 28,191 · Copper Library 6,055", each plan a link to its page. */
private fun FlowContent.planLinks(worldId: Int, asks: List<PlanAsk>) {
    asks.forEachIndexed { index, ask ->
        if (index > 0) +" · "
        a(classes = "set-it-to__plan") {
            href = Link.Worlds.world(worldId).project(ask.projectId).to
            +ask.projectName
        }
        +" ${"%,d".format(ask.left)}"
    }
}

private fun planCounts(asks: List<PlanAsk>): String =
    asks.joinToString(", ") { "${it.projectName} ${"%,d".format(it.left)}" }

/** "Oak, Acacia, Birch, Cherry and Jungle". */
private fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/**
 * Every mode the farm has, asked for or not, with its rate and what the plans ask of it.
 *
 * A mode nobody asks for says so, and says why when another mode makes its item faster: the list
 * is also how a player sees that Azalea Mode is not being skipped by mistake.
 */
private fun FlowContent.everyMode(asks: FarmAsks) {
    val askedById = asks.asked.associateBy { it.modeId }
    val modes = asks.rates.filter { it.modeId != null }.groupBy { it.modeId }
        .values.sortedBy { it.first().modePosition }

    div("set-it-to__every") {
        span("section-label") { +"Every mode · ${modes.size}" }
        div("set-it-to__modes") {
            modes.forEach { rates ->
                val first = rates.first()
                val asked = askedById[first.modeId]
                val shown = asked?.setBy?.let { s -> rates.firstOrNull { it.itemId == s.itemId } }
                    ?: rates.maxBy { it.ratePerHour }
                div("set-it-to__mode-row") {
                    span("set-it-to__mode-name") { +(first.modeName ?: "") }
                    span("set-it-to__mode-rate") {
                        +"${splitKind(itemLabel(shown.itemId, asks)).first} · ${"%,d".format(shown.ratePerHour)}/hr"
                    }
                    span("set-it-to__mode-asked") {
                        if (asked != null) {
                            val setBy = asked.setBy ?: asked.items.first()
                            val plans = asked.planIds.size
                            +"${"%,d".format(setBy.left)} for $plans ${if (plans == 1) "plan" else "plans"}"
                        } else {
                            +notAskedReason(shown.itemId, shown.ratePerHour, asks)
                        }
                    }
                    span("set-it-to__mode-time") {
                        +(asked?.hoursLeft?.let { runningTime(it) } ?: "—")
                    }
                }
            }
        }
    }
}

/** "not asked for: Oak Mode is faster" when the item is asked of a faster mode, else "not asked for". */
private fun notAskedReason(itemId: String, ratePerHour: Int, asks: FarmAsks): String {
    val faster = asks.asked.firstOrNull { mode ->
        mode.items.any { it.itemId == itemId && it.ratePerHour > ratePerHour }
    }
    return if (faster != null) "not asked for: ${faster.modeName} is faster" else "not asked for"
}

/** The item's display name where a plan asked for it, else a name made from its id. */
private fun itemLabel(itemId: String, asks: FarmAsks): String =
    asks.asked.flatMap { it.items }.firstOrNull { it.itemId == itemId }?.itemName
        ?: itemId.substringAfterLast(':').split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
