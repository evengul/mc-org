package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.world.ValidateWorldMemberRole
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
 * what. Only the terminal project's plan totals need a read of their own, and those degrade
 * to zeroes rather than failing the page.
 */
internal suspend fun graphViewOf(roadmap: Roadmap): RoadmapGraphView {
    val terminal = RoadmapGraphLayout.terminalOf(roadmap)
    // The column is about one terminal project; the grid below is about the whole world, so a
    // world with two independent chains does not lose the farms feeding the one not drawn.
    val columnProducers = terminal?.let { producersOf(roadmap, it.projectId) }.orEmpty()
    val allProducers = allProducersOf(roadmap)

    val data = terminal
        ?.let { GetRoadmapGraphDataStep(it.projectId).process(Unit).getOrNull() }
        ?: RoadmapGraphData.EMPTY
    val percent = terminal
        ?.let { GetTerminalProgressStep(it.projectId).process(Unit).getOrNull() }
        ?: 0
    val topMaterials = terminal
        ?.let { GetTopHandMaterialsStep(it.projectId).process(Unit).getOrNull() }
        .orEmpty()

    val handGathered = if (data.byHand > 0) {
        RoadmapGraphLayout.HandGathered(
            items = data.byHand,
            materials = data.handMaterials,
            concentration = concentrationOf(topMaterials, data.byHand),
        )
    } else {
        null
    }

    val terminalStats = RoadmapGraphLayout.TerminalStats(
        fromFarms = data.fromFarms,
        byHand = data.byHand,
        craftRows = data.craftRows,
        openQuestions = data.openQuestions,
        percentComplete = percent,
    )

    val graph = terminal?.let {
        RoadmapGraphLayout.of(roadmap, columnProducers, handGathered, terminalStats)
    }

    val sequence = terminal?.let { RoadmapGraphLayout.sequenceNodesOf(roadmap, it) }.orEmpty()
    val start = sequence.firstOrNull()

    // A project with no edge in either direction is in nobody's chain. Split by state:
    // an unfinished one is work you can do whenever, a *finished* one that supplies
    // nothing is a data gap — it was built, so something should be flowing out of it.
    val connected = roadmap.edges.flatMapTo(mutableSetOf()) { listOf(it.fromNodeId, it.toNodeId) }
    val isolated = roadmap.nodes.filter { it.projectId !in connected }

    return RoadmapGraphView(
        roadmap = roadmap,
        graph = graph,
        startHere = start,
        startHereNote = start?.let { startNoteFor(roadmap, it, sequence.size) },
        producerCount = allProducers.size,
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
        terminal = terminal,
        terminalStats = terminalStats,
        manualEdgeNote = manualEdgeNoteFor(roadmap),
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
private fun startNoteFor(roadmap: Roadmap, start: RoadmapNode, sequenceSize: Int): String {
    val waitsOn = roadmap.edges
        .filter { it.fromNodeId == start.projectId }
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
    return "$lead Every other edge is derived from what your projects actually need."
}
