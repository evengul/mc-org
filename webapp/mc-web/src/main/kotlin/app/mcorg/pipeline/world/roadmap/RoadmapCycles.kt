package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.world.RoadmapCycle
import app.mcorg.domain.model.world.RoadmapCycleOption

/**
 * Finds the loops in a derived dependency graph and picks one edge per loop to set aside
 * (MCO-460).
 *
 * ## Why cycles are not a data error
 *
 * The cobblestone farm requires 20 gunpowder for TNT. The witch farm produces gunpowder. The
 * witch farm requires cobblestone. The cobblestone farm produces cobblestone. Both edges are
 * **true**, derived from real demand — nothing here is bad data to clean up. The loop is a fact
 * about the world, and which farm to build first is a sequencing decision a person makes.
 *
 * ## The threshold breaks most loops, and only loops
 *
 * The asymmetry is the way out: **20 gunpowder is not a prerequisite; 75,151 cobblestone is.**
 * That judgement is one the world already carries — MCO-401's farm-scale threshold is precisely
 * "the quantity above which this is worth a farm" — so a loop containing a sub-threshold claim
 * breaks there, on a principle the product already committed to rather than by picking an
 * arbitrary edge to cut.
 *
 * It is applied **here, inside a detected loop**, and not to the edge query. A Beacon needing 32
 * iron from the Iron Farm is a perfectly good dependency with nothing contradictory about it;
 * thresholding every edge to solve a problem that only arises in loops would quietly delete most
 * of the roadmap. Small edges are only ever a problem when they are load-bearing in a cycle, so
 * that is the only place the rule fires.
 *
 * What survives is two or more farms each needing a farm-scale amount of the other, where no
 * principle picks a winner and only a person can.
 *
 * ## Detected, not inferred
 *
 * `calculateLayers` used to leave cycle members unplaced and drop them at depth 0, with a doc
 * comment saying cycles "are not supposed to exist". Reading the loop back out of *what BFS
 * failed to place* does not work as a diagnosis: everything downstream of a loop is also
 * unplaced, so that set names innocent projects as cycle members. This runs Tarjan's algorithm
 * and reports strongly connected components of size ≥ 2 — the actual loops, and only those.
 *
 * ## The guess, and why there is one
 *
 * A person picks which project comes first, but the roadmap has to render before they answer,
 * and rendering two projects that each claim to block the other is precisely the bug MCO-460
 * filed. So each loop is broken at its **smallest claim** — the edge carrying the least demand,
 * ties broken by project id so a reload never reshuffles the page. That is a guess in the same
 * spirit as the threshold: the weakest link is the one you would most easily do by hand. The
 * page says it is a guess and offers the alternatives.
 *
 * One edge is never the guess while any other will do: a declared `project_dependencies` row
 * (MCO-302), which carries no quantity because a person wrote it rather than a plan deriving it.
 * Overriding somebody's explicit "dig the perimeter first" to tidy up a loop they did not create
 * would be the wrong way round.
 *
 * ## One edge per loop is not one edge per component
 *
 * A two-farm loop has one edge to cut and is done. A component of five farms that each need a
 * little of the other four's output has eighteen internal edges, and removing the single
 * smallest leaves it every bit as strongly connected as before. This used to detect once and
 * break once, which meant that on any world denser than a pair the loop survived, `calculateLayers`
 * placed nobody, and every project fell through to its depth-0 floor — a roadmap reading
 * "1 layer" with its sequence band ordered by project *name*, and "START HERE" pointing at a
 * project the same page called blocked. Even hit it on the first world he imported six farms
 * into: the order was there in the data the whole time.
 *
 * So this breaks, re-detects, and repeats until the graph is acyclic. Terminating is free —
 * every round removes at least one pair from a finite set — and the smallest-claim rule does the
 * right thing cumulatively: the footnote-sized edges go first and silently, and what is left to
 * ask about is the handful of genuinely balanced pairs. On that world it cut eight edges, seven
 * of them under the threshold, and turned one flat layer into a seven-deep chain with a single
 * question left for a person.
 */
object RoadmapCycles {

    /**
     * Every loop in [edges], each with its alternatives and the edge set aside to break it.
     *
     * A dense component appears once per cut it takes, since each cut is a separate ordering
     * fact: the same five farms can be named by several entries, each naming the pair it
     * separated and offering the alternatives that were still live at that point.
     *
     * **All** loops are returned, including the ones the threshold settles on its own — the
     * caller needs every `breaking` edge to lay the graph out, and only shows the subset where
     * [RoadmapCycle.needsAnAnswer]. Filtering here would leave the quiet ones unbroken and put
     * two contradicting projects back on the page.
     *
     * [edges] should already have the user's saved answers removed — a pair someone has
     * ordered is not an open question, and re-detecting it would put the prompt back on the
     * page after it was answered.
     */
    fun detect(edges: List<ProjectResourceEdge>, farmScaleThreshold: Int): List<RoadmapCycle> {
        if (edges.isEmpty()) return emptyList()

        // One edge per ordered pair: a consumer needing three different items from the same
        // producer is one dependency, and would otherwise produce three identical options.
        // The largest claim represents the pair, and a derived edge beats a declared one
        // (null quantity) so the pair keeps a number to show wherever it has one.
        var byPair = edges
            .groupBy { it.consumerId to it.producerId }
            .mapValues { (_, group) -> group.maxBy { it.quantity ?: -1L } }

        val found = mutableListOf<RoadmapCycle>()

        // Break, re-detect, repeat — see the class note on why one pass is not enough.
        while (true) {
            val dependencies = byPair.values.groupBy({ it.consumerId }, { it.producerId })
            val components = stronglyConnectedComponents(dependencies).filter { it.size >= 2 }
            if (components.isEmpty()) break

            val round = components.map { component -> component.toCycle(byPair, farmScaleThreshold) }
            found += round

            // Each round removes at least one pair from a finite set, so this terminates.
            val setAside = round.mapTo(mutableSetOf()) {
                it.breaking.firstProjectId to it.breaking.waitingProjectId
            }
            byPair = byPair.filterKeys { it !in setAside }
        }

        return found.sortedBy { it.projectNames.firstOrNull() ?: "" }
    }

    private fun Set<Int>.toCycle(
        byPair: Map<Pair<Int, Int>, ProjectResourceEdge>,
        farmScaleThreshold: Int,
    ): RoadmapCycle {
        // Only edges with both ends inside the component are part of the loop; an edge leaving
        // it is an ordinary dependency and removing it would break nothing.
        val internal = byPair.values.filter { it.consumerId in this && it.producerId in this }

        val options = internal
            .map { edge ->
                RoadmapCycleOption(
                    // Setting aside "consumer needs producer" frees the consumer: it no longer
                    // waits, so it is the one that comes first.
                    firstProjectId = edge.consumerId,
                    firstProjectName = edge.consumerName,
                    waitingProjectId = edge.producerId,
                    waitingProjectName = edge.producerName,
                    itemName = edge.itemName,
                    quantity = edge.quantity,
                )
            }
            .sortedWith(
                compareBy(
                    // A null quantity is a declared `project_dependencies` row — someone said
                    // "dig the perimeter first" in as many words. Never break a cycle at a
                    // human's own decision while a derived edge is available to break instead,
                    // so those sort last and are only ever the guess when nothing else is.
                    { it.quantity == null },
                    { it.quantity ?: Long.MAX_VALUE },
                    { it.firstProjectId },
                    { it.waitingProjectId },
                )
            )

        val names = internal
            .flatMap { listOf(it.consumerId to it.consumerName, it.producerId to it.producerName) }
            .distinctBy { it.first }
            .sortedBy { it.second }

        // Sorted smallest-first above, so the head is the smallest claim with a stable
        // tiebreak. `first()` is safe: a component of size >= 2 has at least two internal
        // edges, or it would not be strongly connected.
        val breaking = options.first()

        return RoadmapCycle(
            projectIds = names.map { it.first },
            projectNames = names.map { it.second },
            options = options,
            breaking = breaking,
            // Below the world's line, the smallest claim is a footnote and giving way is the
            // obvious answer rather than a judgement call — so the loop is broken silently and
            // never reaches the page. A declared dependency (null quantity) is never a footnote:
            // a person wrote it, so a loop resting on one is always worth asking about.
            needsAnAnswer = (breaking.quantity ?: Long.MAX_VALUE) >= farmScaleThreshold,
        )
    }

    /**
     * Tarjan's strongly connected components, iterative.
     *
     * Iterative rather than the shorter recursive form because the graph is user data: a long
     * enough dependency chain would overflow the stack, and a roadmap that 500s on a big world
     * is worse than one that takes a few more lines to build.
     */
    private fun stronglyConnectedComponents(dependencies: Map<Int, List<Int>>): List<Set<Int>> {
        val nodes = (dependencies.keys + dependencies.values.flatten()).toSet()

        val index = HashMap<Int, Int>()
        val lowLink = HashMap<Int, Int>()
        val onStack = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        val components = mutableListOf<Set<Int>>()
        var nextIndex = 0

        for (root in nodes) {
            if (root in index) continue

            // Each frame is a node plus how far through its neighbours we have walked.
            val callStack = ArrayDeque<Frame>()
            callStack.addLast(Frame(root, 0))
            index[root] = nextIndex
            lowLink[root] = nextIndex
            nextIndex++
            stack.addLast(root)
            onStack.add(root)

            while (callStack.isNotEmpty()) {
                val frame = callStack.last()
                val neighbours = dependencies[frame.node].orEmpty()

                if (frame.next < neighbours.size) {
                    val neighbour = neighbours[frame.next]
                    frame.next++
                    when {
                        neighbour !in index -> {
                            index[neighbour] = nextIndex
                            lowLink[neighbour] = nextIndex
                            nextIndex++
                            stack.addLast(neighbour)
                            onStack.add(neighbour)
                            callStack.addLast(Frame(neighbour, 0))
                        }
                        neighbour in onStack ->
                            lowLink[frame.node] = minOf(lowLink.getValue(frame.node), index.getValue(neighbour))
                    }
                } else {
                    callStack.removeLast()
                    callStack.lastOrNull()?.let { parent ->
                        lowLink[parent.node] = minOf(lowLink.getValue(parent.node), lowLink.getValue(frame.node))
                    }
                    if (lowLink.getValue(frame.node) == index.getValue(frame.node)) {
                        val component = mutableSetOf<Int>()
                        while (true) {
                            val popped = stack.removeLast()
                            onStack.remove(popped)
                            component.add(popped)
                            if (popped == frame.node) break
                        }
                        components.add(component)
                    }
                }
            }
        }

        return components
    }

    private data class Frame(val node: Int, var next: Int)
}
