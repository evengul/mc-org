package app.mcorg.pipeline.world.roadmap

import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.world.Roadmap
import app.mcorg.domain.model.world.RoadmapNode
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.resources.GetUnfinishedProductionsStep
import app.mcorg.pipeline.resources.ScenarioDemand
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.pipeline.world.roadmap.ordering.GetManualOrderingsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.RoadmapGraphView
import app.mcorg.presentation.templated.dsl.pages.roadmapGraphPage
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall

/**
 * `GET /worlds/{worldId}/roadmap` (MCO-288, graph view MCO-469) — the world's derived
 * project sequence.
 *
 * Read-only, so world membership (enforced by the route's plugins) is the whole
 * authorization story; the admin check only decides what the header offers.
 *
 * One view. There used to be a second, a table of every project at `?view=table`, kept as the
 * form that survived hundreds of rows, a screen reader and a 375px viewport. The graph page now
 * carries its own table of the work (TO BUILD), its own 375px layout and its own nothing-built
 * state, so the table was a second place for every fix to land and was removed (MCO-529). An old
 * `?view=table` link lands here like any other.
 */
suspend fun ApplicationCall.handleGetWorldRoadmap() {
    val user = getUser()
    val worldId = getWorldId()
    val isAdmin = ValidateWorldMemberRole<Unit>(user, Role.ADMIN, worldId).process(Unit) is Result.Success

    handlePipeline(
        onSuccess = { roadmap: Roadmap ->
            respondHtml(roadmapGraphPage(user, graphViewOf(roadmap), isWorldAdmin = isAdmin))
        }
    ) {
        GetWorldRoadMapStep(worldId).run(Unit)
    }
}

/**
 * Assembles everything the graph template renders.
 *
 * Producers, the farms still to build and both list sections all come out of the roadmap's own
 * edges — one derivation, so the graph cannot disagree with the table about what blocks
 * what. Only each final project's plan totals need reads of their own, and those degrade to
 * zeroes rather than failing the page.
 */
internal suspend fun graphViewOf(roadmap: Roadmap): RoadmapGraphView {
    // Every project the world drains into, not the one that won a tie-break (MCO-563). Only the
    // ones drawn as panels need numbers of their own.
    // Each project's planned demand, farm and hand alike: what ranks the final projects, and what
    // lets a build that is nothing but hand work count as one. Degrades to ranking by supply lines.
    val demand = GetWorldDemandTotalsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()
    val terminals = RoadmapGraphLayout.terminalsOf(roadmap, demand)
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

    val graphData = drawn.associate { terminal ->
        terminal.projectId to (GetRoadmapGraphDataStep(terminal.projectId).process(Unit).getOrNull() ?: RoadmapGraphData.EMPTY)
    }

    // The farms still to build are a set rather than a queue (MCO-571): a TO BUILD table, and a band
    // for whatever order really exists among them — a numbered list when that order is total (5B). The same in every world (MCO-544, frame 5A) — a
    // world where nothing produces yet is the same page with no supply column, not a second page.
    // "Building" is read from progress rather than declared state (MCO-579).
    val started = GetStartedProjectsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()
    val toBuild = RoadmapToBuild.of(roadmap, terminals, started)
    val producing = columnProducers.isNotEmpty()

    // Whatever makes something; the rows of TO BUILD that are not farms have nothing to gather instead.
    val farms = GetFarmIdsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()

    // Decommissioned farms have no edges, so nothing above would ever mention them (MCO-541).
    val stoppedFarms = GetStoppedFarmsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()

    // "promised" and "yours either way", and what each stopped farm costs (MCO-572): differences
    // between hand lists, so each needs a plan derived as if farms were built. Cached; see
    // [ScenarioDemand]. The split is drawn whichever layout the world gets — a fresh world is where
    // most of its hand list is already spoken for by farms the user has just created.
    val building = toBuild.rows.mapTo(mutableSetOf()) { it.projectId }
    val productions = GetUnfinishedProductionsStep(roadmap.worldId).process(Unit).getOrNull().orEmpty()
    val demandItems = GetDemandItemIdsStep(drawnIds).process(Unit).getOrNull().orEmpty()
    // A total order (5B) also asks for each prefix of it built — "hand list left after" each step.
    val chain = toBuild.chain?.map { it.projectId }.orEmpty()
    val prefixes = HandListSplit.chainPrefixes(chain, drawnIds, productions, demandItems)
    val wanted = HandListSplit.wanted(drawnIds, building, stoppedFarms.map { it.projectId }, productions, demandItems)
        .mapValues { (panel, sets) -> (sets + prefixes[panel].orEmpty()).distinct() }
    val scenarioTotals = ScenarioDemand.handTotals(roadmap.worldId, wanted, productions)
    val chainLeftAfter = HandListSplit.leftAfter(
        chain, drawnIds.associateWith { graphData.getValue(it).byHand }, scenarioTotals, productions, demandItems,
    )
    val splits = drawnIds.associateWith { id ->
        val byHand = graphData.getValue(id).byHand
        val asIfBuilt = if (building.isEmpty()) byHand else scenarioTotals[id]?.get(building)
        HandListSplit.split(byHand, asIfBuilt)
    }
    val stopped = stoppedFarms.map { farm ->
        farm.copy(
            uncoveredItems = HandListSplit.stoppedCost(
                farm.projectId, drawnIds, building, splits.mapValues { it.value?.eitherWay }, scenarioTotals,
                productions, demandItems,
            )
        )
    }

    val terminalStats = drawn.associate { terminal ->
        val data = graphData.getValue(terminal.projectId)
        val percent = GetTerminalProgressStep(terminal.projectId).process(Unit).getOrNull() ?: 0
        terminal.projectId to RoadmapGraphLayout.TerminalStats(
            fromFarms = data.fromFarms,
            byHand = data.byHand,
            craftRows = data.craftRows,
            openQuestions = data.openQuestions,
            percentComplete = percent,
            // The same set the column draws, so "from 16 farms" and the run headers count the
            // same farms — a panel claiming more suppliers than the column shows is the kind of
            // disagreement this page exists to avoid.
            farms = columnProducers.count { (it.itemsByTerminal[terminal.projectId] ?: 0L) > 0 },
            split = splits[terminal.projectId],
        )
    }
    val handGathered = handGatheredOf(
        drawn.associate { it.projectId to GetHandMaterialsStep(it.projectId).process(Unit).getOrNull().orEmpty() }
    )?.let { hand ->
        hand.copy(promised = splits.values.sumOf { it?.promised ?: 0L })
    }

    // With no farm producing there is no column to lay out, and the page draws the panels as a row
    // instead (frame 5A) — so there is only a graph once something feeds a drawn panel.
    val graph = if (!producing) {
        null
    } else {
        RoadmapGraphLayout.of(
            roadmap,
            columnProducers,
            handGathered,
            demand,
            promised = RoadmapGraphLayout.Promised(toBuild.rows.size, toBuild.promisedByTerminal),
            splitRows = splits.values.any { (it?.promised ?: 0L) > 0 },
        )
    }

    // FINISH THESE FIRST replaces START HERE whenever there is anything to build: naming one farm
    // to start would rank a set the page has just said it cannot rank. With nothing left to build,
    // the first unfinished final project is where to start.
    val start = if (toBuild.isEmpty) startOf(terminals) else null

    // A project with no edge in either direction is in nobody's chain. Split by state:
    // an unfinished one is work you can do whenever, a *finished* one that supplies
    // nothing is a data gap — it was built, so something should be flowing out of it.
    // A final project that is nothing but hand work has no edge either, and it is not
    // do-whenever: it has a panel, or a place in "+N more" past the cap. Every final project is
    // excluded, not only the drawn ones, so none is listed twice.
    val terminalIds = terminals.mapTo(mutableSetOf()) { it.projectId }
    val connected = roadmap.edges.flatMapTo(mutableSetOf()) { listOf(it.fromNodeId, it.toNodeId) }
    val isolated = roadmap.nodes.filter { it.projectId !in connected && it.projectId !in terminalIds }

    return RoadmapGraphView(
        roadmap = roadmap,
        graph = graph,
        toBuild = toBuild,
        producing = producing,
        startHere = start,
        startHereNote = start?.let { readyNoteFor(terminals.filter { terminal -> !terminal.state.isTerminal }) },
        producerCount = allProducers.size,
        // "16 · 2 feed more than one", frame 4A's GRAPH SHAPE: the farms in the column, and how many
        // of them sit in its shared run.
        sharedProducers = columnProducers.count { producer ->
            drawn.count { (producer.itemsByTerminal[it.projectId] ?: 0L) > 0 } > 1
        },
        finalProjectCount = terminals.size,
        handTotals = handGathered,
        hiddenTerminals = terminals.drop(RoadmapGraphLayout.MAX_TERMINALS),
        chainLeftAfter = chainLeftAfter,
        byHandNow = graphData.values.sumOf { it.byHand },
        started = started,
        farms = farms,
        stopped = stopped,
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
 * Where to start once nothing is left to build before the final projects: the first of them that
 * is still to do. (With farms left to build, FINISH THESE FIRST speaks instead.)
 *
 * Null when every final project is finished. [RoadmapGraphLayout.terminalsOf] falls back to a
 * *finished* project so a built world still draws its graph, and taking that fallback here put a
 * completed build under "START HERE" with a Not Started badge.
 */
internal fun startOf(terminals: List<RoadmapNode>): RoadmapNode? =
    terminals.firstOrNull { !it.state.isTerminal }

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
