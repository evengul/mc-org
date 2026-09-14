package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.world.roadmap.ordering.GetManualOrderingsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.RoadmapGraphView
import app.mcorg.presentation.templated.dsl.pages.roadmapGraphPage
import app.mcorg.presentation.templated.dsl.pages.roadmapPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.uri

/**
 * `GET /worlds/{worldId}/roadmap` (MCO-288, graph view MCO-469) — the world's derived
 * project sequence.
 *
 * Read-only, so world membership (enforced by the route's plugins) is the whole
 * authorization story; the admin check only decides what the header offers.
 *
 * Two views over one derivation. The **graph** is the default: it separates sequence from
 * supply, which is the question the page exists to answer. The **table** stays reachable at
 * `?view=table` unchanged — it is still the form that survives hundreds of rows, a screen
 * reader and a 375px viewport, and the graph deliberately does not try to be all three.
 */
suspend fun ApplicationCall.handleGetWorldRoadmap() {
    val user = getUser()
    val worldId = getWorldId()
    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success
    val wantsTable = request.uri.contains("view=table")

    handlePipeline(
        onSuccess = { roadmap: Roadmap ->
            if (wantsTable) {
                respondHtml(roadmapPage(user, roadmap, isWorldAdmin = isAdmin))
            } else {
                respondHtml(roadmapGraphPage(user, graphViewOf(roadmap), isWorldAdmin = isAdmin))
            }
        }
    ) {
        GetWorldRoadMapStep(worldId).run(Unit)
    }
}

/**
 * Assembles everything the graph template renders.
 *
 * Producers, the sequence band and both list sections all come out of the roadmap's own
 * edges — one derivation, so the graph cannot disagree with the table about what blocks
 * what. Only each final project's plan totals need reads of their own, and those degrade to
 * zeroes rather than failing the page.
 */
internal suspend fun graphViewOf(roadmap: Roadmap): RoadmapGraphView {
    // Every project the world drains into, not the one that won a tie-break (MCO-563). Only the
    // ones drawn as panels need numbers of their own.
    val terminals = RoadmapGraphLayout.terminalsOf(roadmap)
    val drawn = terminals.take(RoadmapGraphLayout.MAX_TERMINALS)
    val drawnIds = drawn.mapTo(mutableSetOf()) { it.projectId }
    // The roster reads `project_dependencies` directly rather than filtering [roadmap.edges]:
    // the derived edge set carries no row ids and no reasons, so it can say that a hand-made
    // ordering exists but not which row to remove. Degrades to an empty roster rather than
    // failing the page, exactly as the final projects' totals do.
    val manualOrderings = GetManualOrderingsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()
    // The column is about the final projects drawn; the grid below is about the whole world, so a
    // farm feeding nothing drawn still appears in some section.
    val columnProducers = producersOf(roadmap, drawnIds)
    val allProducers = allProducersOf(roadmap)

    val terminalStats = drawn.associate { terminal ->
        val data = GetRoadmapGraphDataStep(terminal.projectId).process(Unit).getOrNull()
            ?: RoadmapGraphData.EMPTY
        val percent = GetTerminalProgressStep(terminal.projectId).process(Unit).getOrNull() ?: 0
        terminal.projectId to RoadmapGraphLayout.TerminalStats(
            fromFarms = data.fromFarms,
            byHand = data.byHand,
            craftRows = data.craftRows,
            openQuestions = data.openQuestions,
            percentComplete = percent,
        )
    }
    val handGathered = handGatheredOf(
        drawn.associate { it.projectId to GetHandMaterialsStep(it.projectId).process(Unit).getOrNull().orEmpty() }
    )

    val graph = RoadmapGraphLayout.of(roadmap, columnProducers, handGathered)

    val sequence = RoadmapGraphLayout.sequenceNodesOf(roadmap, terminals)
    val start = startOf(sequence, terminals)

    // A project with no edge in either direction is in nobody's chain. Split by state:
    // an unfinished one is work you can do whenever, a *finished* one that supplies
    // nothing is a data gap — it was built, so something should be flowing out of it.
    val connected = roadmap.edges.flatMapTo(mutableSetOf()) { listOf(it.fromNodeId, it.toNodeId) }
    val isolated = roadmap.nodes.filter { it.projectId !in connected }

    return RoadmapGraphView(
        roadmap = roadmap,
        graph = graph,
        startHere = start,
        startHereNote = start?.let {
            if (sequence.isEmpty()) {
                readyNoteFor(terminals.filter { terminal -> !terminal.state.isTerminal })
            } else {
                startNoteFor(roadmap, it, sequence.size)
            }
        },
        producerCount = allProducers.size,
        feeding = feedingOf(columnProducers),
        producerRows = allProducers
            .sortedWith(compareByDescending<RoadmapGraphLayout.Producer> { it.items }.thenBy { it.name })
            .map {
                RoadmapGraphView.ProducerRow(
                    projectId = it.projectId,
                    name = it.name,
                    items = it.items,
                    edges = it.edges,
                )
            },
        unchained = isolated
            .filter { !it.state.isTerminal }
            .map {
                RoadmapGraphView.UnchainedRow(
                    projectId = it.projectId,
                    name = it.projectName,
                    note = taskNoteFor(it),
                    tone = if (it.tasksTotal > 0 && it.tasksCompleted >= it.tasksTotal) {
                        RoadmapGraphLayout.Tone.GREEN
                    } else {
                        RoadmapGraphLayout.Tone.MUTED
                    },
                )
            },
        terminals = drawn,
        terminalStats = terminalStats,
        manualEdgeNote = manualEdgeNoteFor(roadmap),
        manualOrderings = manualOrderings,
        // What the roster contrasts itself against. An edge that names a resource was derived
        // from a farm's output meeting a project's demand; nobody typed it, and it cannot be
        // deleted from the roster — which is what the section's footer note says.
        generatedEdgeCount = roadmap.edges.count { it.itemName != null },
        // Every one of them, not just the first: the design was drawn against a world with
        // exactly one such farm, and silently hiding the second would be the same class of bug
        // the design set out to fix.
        dataGaps = isolated
            .filter { it.state == ProjectState.DONE }
            .map {
                RoadmapGraphView.DataGap(
                    projectId = it.projectId,
                    message = "${it.projectName} is done but declares no productions — " +
                        "it supplies nothing, and its output is still on the hand-gathering list",
                )
            },
    )
}

/** "The only project with anything waiting on it. Waits on Cobble farm for 100,000 X." */
internal fun startNoteFor(roadmap: Roadmap, start: RoadmapNode, sequenceSize: Int): String {
    // Only a blocking edge is something to wait on. A finished farm supplying the project is the
    // opposite, and reading every edge printed "waits on First trading setup for 14,976 Glass"
    // about a farm that had been running for months (MCO-563).
    val waitsOn = roadmap.edges
        .filter { it.fromNodeId == start.projectId && it.isBlocking }
        .maxByOrNull { it.quantity ?: Long.MIN_VALUE }

    val lead = if (sequenceSize == 1) {
        "The only project with anything waiting on it."
    } else {
        "First of $sequenceSize projects still to build."
    }

    val tail = waitsOn?.let { edge ->
        val amount = edge.quantity?.let { "${RoadmapGraphLayout.format(it)} " } ?: ""
        edge.itemName?.let { " Waits on ${edge.toNodeName} for $amount$it." }
            ?: " Waits on ${edge.toNodeName}."
    } ?: ""

    return lead + tail
}

/**
 * "Nothing is left to build before it. Copper Library is ready too." — the note when every
 * project feeding the final projects is already done, so the first of them is where to start.
 */
internal fun readyNoteFor(terminals: List<RoadmapNode>): String {
    val others = terminals.drop(1)
    val tail = when (others.size) {
        0 -> ""
        1 -> " ${others.single().projectName} is ready too."
        else -> " ${others.size} more final projects are ready too."
    }
    return "Nothing is left to build before it.$tail"
}

/**
 * Where to start: the first project in the band, or — with nothing left to build before them —
 * the first final project that is still to do.
 *
 * Null when every final project is finished. [RoadmapGraphLayout.terminalsOf] falls back to a
 * *finished* project so a built world still draws its graph, and taking that fallback here put a
 * completed build under "START HERE" with a Not Started badge.
 */
internal fun startOf(sequence: List<RoadmapNode>, terminals: List<RoadmapNode>): RoadmapNode? =
    sequence.firstOrNull() ?: terminals.firstOrNull { !it.state.isTerminal }

/**
 * "34,313 items from 6 farms" — the supply column's farms and what they feed the drawn final
 * projects, or null when they feed nothing.
 *
 * Both numbers come from [producers], the column itself. The row used to sum the drawn projects'
 * edges and divide by the whole world's farm count, so a world where one hand-made ordering left
 * a single small project drawn read "34,313 items from 22 farms" beside a column of six.
 */
internal fun feedingOf(producers: List<RoadmapGraphLayout.Producer>): String? {
    val items = producers.sumOf { it.items }
    if (items <= 0) return null
    val farms = if (producers.size == 1) "farm" else "farms"
    return "${RoadmapGraphLayout.format(items)} items from ${producers.size} $farms"
}

private fun taskNoteFor(node: RoadmapNode): String = when {
    node.tasksTotal == 0 -> "no tasks"
    node.tasksCompleted >= node.tasksTotal -> "✓ ${node.tasksCompleted} / ${node.tasksTotal} tasks — ready to close"
    else -> "${node.tasksCompleted} / ${node.tasksTotal} tasks"
}

/**
 * The callout under the graph — only rendered when a hand-made ordering is actually doing
 * work. A world with no manual edges has nothing to say here, and an empty callout reads
 * as a bug.
 */
private fun manualEdgeNoteFor(roadmap: Roadmap): String? {
    val manual = roadmap.edges.filter { it.itemName == null && it.isBlocking }
    if (manual.isEmpty()) return null
    // Name the pair and its direction. Naming one end alone ("New slime farm is the only
    // hand-made ordering") reads as nonsense — an ordering is a relationship, not a project.
    val pairs = manual.map { "${it.toNodeName} before ${it.fromNodeName}" }.distinct()
    val lead = if (pairs.size == 1) {
        "${pairs.first()} is the only ordering here somebody set by hand."
    } else {
        "${pairs.size} orderings here were set by hand: ${pairs.joinToString("; ")}."
    }
    return "$lead Everything else here is derived from what your projects actually need."
}
