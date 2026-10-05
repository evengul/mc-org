package app.mcorg.pipeline.world.roadmap

/**
 * The roadmap's hand-list differences (MCO-572, frame 4A).
 *
 * Every number here is one hand list minus another, never a sum of supply lines:
 *
 * * **promised** — a panel's hand list now, minus its hand list with every farm still to build
 *   finished. What building them takes off your hands.
 * * **yours either way** — that second hand list. What stays yours whatever you build.
 * * **a stopped farm's cost** — the hand list with every farm still to build finished, minus the
 *   same with this stopped farm running again, summed over the drawn panels.
 *
 * Supply lines were the old way to get the first and the last, and they cannot add up: a farm's
 * 32,999 counts ingots that were never on the hand list, only the ore under them. Two differences
 * of hand lists add up by construction — promised and yours either way always sum to by hand now.
 *
 * Pure: the plans are derived and cached by [app.mcorg.pipeline.resources.ScenarioDemand]; this
 * only decides which ones to ask for and what to subtract.
 */
object HandListSplit {

    data class Split(val promised: Long, val eitherWay: Long)

    /**
     * Which scenarios each drawn panel needs: the farms still to build, and each stopped farm on top
     * of them — but only a stopped farm whose output appears somewhere in that panel's plan. One
     * that makes nothing the panel touches costs it nothing, and proving so would cost a derivation.
     *
     * With nothing to build, "everything unfinished built" is the world as it is, so the stopped
     * scenarios stand alone and the base needs no derivation at all.
     */
    fun wanted(
        drawn: Collection<Int>,
        building: Set<Int>,
        stopped: Collection<Int>,
        productions: Map<Int, Set<String>>,
        demandItems: Map<Int, Set<String>>,
    ): Map<Int, List<Set<Int>>> = drawn.associateWith { panel ->
        buildList {
            if (building.isNotEmpty()) add(building)
            stopped
                .filter { touches(it, panel, productions, demandItems) }
                .forEach { add(building + it) }
        }
    }

    /**
     * A panel's split, or null when the as-if-built hand list is unknown.
     *
     * Clamped so the halves still add up if building a farm ever *grew* a hand list — a supplied
     * item can make a different recipe the cheapest, one that wants a raw material the old one did
     * not. Then nothing is promised and all of it is yours either way, which is what the reader
     * would find.
     */
    fun split(byHandNow: Long, asIfBuilt: Long?): Split? {
        if (asIfBuilt == null) return null
        val either = asIfBuilt.coerceIn(0, byHandNow.coerceAtLeast(0))
        return Split(promised = byHandNow - either, eitherWay = either)
    }

    /**
     * What one stopped farm costs the drawn panels: Σ (base − base with it running), never negative
     * per panel. Zero when it touches none of them; null when a hand list it needed is unknown, or
     * there are no panels to measure against.
     *
     * @param base each panel's hand list with every farm still to build finished — [split]'s
     *   `eitherWay`, which is the hand list now when nothing is left to build.
     */
    fun stoppedCost(
        farm: Int,
        drawn: Collection<Int>,
        building: Set<Int>,
        base: Map<Int, Long?>,
        totals: Map<Int, Map<Set<Int>, Long>>,
        productions: Map<Int, Set<String>>,
        demandItems: Map<Int, Set<String>>,
    ): Long? {
        // No panel is nothing measured, not nothing lost: a world with no final project drawn would
        // otherwise read "nothing it made is missing" about every farm it ever stopped.
        if (drawn.isEmpty()) return null
        var cost = 0L
        drawn.filter { touches(farm, it, productions, demandItems) }.forEach { panel ->
            val before = base[panel] ?: return null
            val after = totals[panel]?.get(building + farm) ?: return null
            cost += (before - after).coerceAtLeast(0)
        }
        return cost
    }

    private fun touches(farm: Int, panel: Int, productions: Map<Int, Set<String>>, demandItems: Map<Int, Set<String>>) =
        productions[farm].orEmpty().any { it in demandItems[panel].orEmpty() }
}
