package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.event.IdeaImported
import app.mcorg.event.eventBus
import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.domain.model.idea.buildTimeModes
import app.mcorg.pipeline.idea.commonsteps.GetIdeaProductionModesStep
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
import app.mcorg.presentation.handler.defaultHandleError
import app.mcorg.presentation.handler.handlePipeline
import io.ktor.server.response.respond
import io.ktor.http.Parameters
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.templated.dsl.pages.importReviewPage
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.domain.model.user.Role
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.serialization.json.Json
import java.time.Instant

data class IdeaForImport(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    /**
     * What to gather — already resolved out of placed forms by [ValidateItemIdsStep].
     *
     * Not the ids the idea recorded. An idea captured from a schematic carries placed
     * block-state ids, and every consumer past this point wants the gathered item (MCO-308).
     */
    val requirements: Map<Item, Int>,
    val production: Map<Item, Int>,
    /**
     * Fluid cell counts, keyed by bucket id — review-time context, not persisted (MCO-396).
     *
     * The requirement is one reusable bucket; this is the number of cells it fills, so the
     * review screen can say "1 water bucket, placed 4,013×" and stay reconcilable.
     */
    val placedCounts: Map<String, Int> = emptyMap(),
    /**
     * Produced item ids this world's version does not have (MCO-456). Dropped from
     * [production] and reported, never fatal — see [ValidateItemIdsStep].
     */
    val unrecordableProductions: List<String> = emptyList(),
    /**
     * The ways this design can be *built*, in the author's order, when there is more than one
     * (MCO-463). Empty for every idea with a single material list, which is most of them.
     *
     * A choice between these is a choice of what the project will cost, so it belongs on the
     * review screen rather than being made silently.
     */
    val buildTimeModes: List<String> = emptyList(),
    /** Which of [buildTimeModes] the requirements and rates above were read from. */
    val chosenBuildTimeMode: String? = null,
)

/**
 * Which idea to import, and — when it can be built more than one way — which way (MCO-463).
 *
 * [buildTimeMode] names a mode rather than carrying its id because the id is a surrogate nothing
 * else references: `replaceIdeaProductionModes` deletes and re-inserts every mode on each save, so
 * an id in a URL would go stale the next time the author edited the design. Names are unique per
 * idea and stable across a save that did not rename them.
 */
internal data class ImportSelection(val ideaId: Int, val buildTimeMode: String? = null)

/** Names the chosen build-time variant, on the review GET and on the import POST alike. */
internal const val BUILD_TIME_MODE_PARAM = "buildTimeMode"

/**
 * The review URL for one build-time variant, carrying everything the current one carries.
 *
 * Every other parameter has to survive the switch — the world, a `forTask` dependency, and an
 * MCO-459 batch queue — because picking a variant is a step *inside* a review, not a way out of
 * one. Dropping the queue here would strand someone halfway through a seven-design batch.
 */
private fun reviewHrefFor(
    ideaId: Int,
    worldId: Int,
    taskId: Int?,
    queue: ImportQueue?,
    mode: String,
): String = buildString {
    append(Link.Ideas.single(ideaId)).append("/import/review")
    append("?worldId=").append(worldId)
    taskId?.let { append("&forTask=").append(it) }
    queue?.hiddenFields()?.forEach { (key, value) ->
        append('&').append(key).append('=').append(value.encodeURLParameter())
    }
    append('&').append(BUILD_TIME_MODE_PARAM).append('=').append(mode.encodeURLParameter())
}

/**
 * Step one of an idea import (MCO-306): show what the idea would create, before creating it.
 *
 * This matters more here than for a schematic. A schematic can be edited in Litematica and
 * re-uploaded; an idea is someone else's list, and this screen is the only place to say
 * "not that part" before it becomes a hundred resource rows.
 *
 * A GET, unlike the schematic flow's review — the material list comes from the database, so
 * the page is reloadable and shareable rather than tied to one upload.
 */
suspend fun ApplicationCall.handleReviewIdeaImport() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()

    val worldId = request.queryParameters["worldId"]?.toIntOrNull()
        ?: return defaultHandleError(
            AppFailure.customValidationError("worldId", "Pick a world to import into")
        )

    val taskId = request.queryParameters["forTask"]?.toIntOrNull()
    val chosenBuildTimeMode = request.queryParameters[BUILD_TIME_MODE_PARAM]?.takeIf { it.isNotBlank() }
    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    // A batch started from a plan's suggestion list (MCO-459), or null for every other door
    // into this screen. Resolved here rather than in the template so a hand-edited URL naming
    // a design the batch never selected degrades to a plain single import.
    val queue = ImportQueue.from(
        request.queryParameters[ImportQueue.QUEUE_PARAM],
        request.queryParameters[ImportQueue.RETURN_PARAM],
    )
    val wizard = queue?.positionOf(ideaId)?.let { position ->
        ImportWizardStep(
            position = position,
            total = queue.size,
            nextHref = queue.nextAfter(ideaId)?.let { queue.reviewHref(it, worldId) },
            returnHref = queue.returnHref(worldId),
        )
    }

    handlePipeline(
        onSuccess = { idea: IdeaForImport ->
            // Already resolved by ValidateItemIdsStep, through the same tables the schematic
            // door reads (MCO-396, MCO-308) — placed forms are the gathered item, fluids are
            // one bucket, non-materials are gone. Nothing to normalize a second time here.
            val requirements = idea.requirements
            respondHtml(
                importReviewPage(
                    user = user,
                    worldId = worldId,
                    worldName = getWorldName(worldId),
                    projectName = idea.name,
                    requirements = requirements,
                    placedCounts = idea.placedCounts,
                    warnings = computeImportWarnings(worldId, requirements),
                    unrecordableProductions = idea.unrecordableProductions,
                    // Only the idea door offers this (MCO-457). A schematic is a file of blocks
                    // to place, with nothing to say about what the thing produces; an idea
                    // carries the rates, which is the whole reason to record a farm you already
                    // built rather than re-typing them into the MCO-298 form.
                    offerAlreadyBuilt = true,
                    wizard = wizard,
                    // Which way this design is being built, when there is more than one way
                    // (MCO-463). Picking a different one re-runs this GET, so the material list,
                    // the warnings and the totals below all move together — the list on screen is
                    // always the list for the selected variant.
                    buildTimeModes = idea.buildTimeModes,
                    chosenBuildTimeMode = idea.chosenBuildTimeMode,
                    reviewHref = { mode -> reviewHrefFor(ideaId, worldId, taskId, queue, mode) },
                    action = Link.Ideas.single(ideaId) + "/import",
                    hiddenFields = buildMap {
                        put("worldId", worldId.toString())
                        taskId?.let { put("forTask", it.toString()) }
                        // The POST has to import the variant the user was looking at, not the
                        // default one it would otherwise re-derive.
                        idea.chosenBuildTimeMode?.let { put(BUILD_TIME_MODE_PARAM, it) }
                        // Carried so the POST can hand the user to the next step without
                        // re-deriving the batch from anywhere but the form it came from.
                        if (wizard != null) putAll(queue!!.hiddenFields())
                    },
                )
            )
        }
    ) {
        ValidateWorldMemberRole<Pair<Int, Int>>(user, Role.ADMIN, worldId).run(worldId to ideaId)
        ValidateVersionRangeStep.run(worldId to ideaId)
        val ideaData = GetIdeaForImportStep.run(ImportSelection(ideaId, chosenBuildTimeMode))
        ValidateItemIdsStep(items).run(ideaData)
    }
}

/**
 * Step two: create the project from the **reviewed** requirements.
 *
 * Only the requirements are reviewed. An idea's productions describe what the farm puts out
 * (MCO-287's supply model) — they are not work you are agreeing to do, so they are not the
 * user's to exclude here and carry through untouched, along with the idea link, the starter
 * tasks and any `forTask` dependency.
 */
suspend fun ApplicationCall.handleImportIdea() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()
    val bus = this.eventBus

    val submitted = this.receiveParameters()
    val worldId = (submitted["worldId"] ?: parameters["worldId"])?.toIntOrNull()
        ?: return run {
            defaultHandleError(AppFailure.customValidationError("worldId", "Invalid or missing worldId parameter"))
        }

    val taskId = (submitted["forTask"] ?: parameters["forTask"])?.toIntOrNull()
    val chosenBuildTimeMode = submitted[BUILD_TIME_MODE_PARAM]?.takeIf { it.isNotBlank() }

    val queue = ImportQueue.from(
        submitted[ImportQueue.QUEUE_PARAM] ?: request.queryParameters[ImportQueue.QUEUE_PARAM],
        submitted[ImportQueue.RETURN_PARAM] ?: request.queryParameters[ImportQueue.RETURN_PARAM],
    )

    // An unchecked checkbox posts nothing at all, so presence is the signal — the same
    // reading every other checkbox on this screen gets.
    val alreadyBuilt = submitted["alreadyBuilt"] != null

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { projectId: Int ->
            // Mid-batch, the next design is where you want to be; at the end of one, the plan
            // you started from. Only a lone import lands on the project it just made, which is
            // MCO-457's rule and still right for the single case (MCO-459).
            val target = queue
                ?.let { it.nextAfter(ideaId)?.let { next -> it.reviewHref(next, worldId) } ?: it.returnHref(worldId) }
                ?: Link.Worlds.world(worldId).project(projectId).to
            // The review page submits as a plain form, so a browser needs a real redirect;
            // an HX-Redirect header would be silently ignored and leave a blank page.
            if (request.headers["HX-Request"] == "true") {
                clientRedirect(target)
            } else {
                response.headers.append("Location", target)
                respond(HttpStatusCode.SeeOther, "")
            }
        }
    ) {
        ValidateWorldMemberRole<Pair<Int, Int>>(user, Role.ADMIN, worldId).run(worldId to ideaId)
        ValidateVersionRangeStep.run(worldId to ideaId)
        // The variant the user was looking at when they submitted. Re-read rather than trusted
        // blind: GetIdeaForImportStep falls back to the author's first if the name no longer
        // matches a build-time mode, which is what happens when the design was edited between the
        // review being rendered and this POST.
        val ideaData = GetIdeaForImportStep.run(ImportSelection(ideaId, chosenBuildTimeMode))
        val validatedIdea = ValidateItemIdsStep(items).run(ideaData)
        val reviewedIdea = ApplyReviewedRequirementsStep(submitted, items, alreadyBuilt).run(validatedIdea)
        val projectId = CreateProjectFromIdeaStep(worldId, taskId, alreadyBuilt).run(reviewedIdea)
        // An already-built import is DONE the moment it exists, so it is world supply now —
        // every stored plan that gathers what it makes is already wrong (MCO-404). An ordinary
        // import is ACTIVE and supplies nothing yet, so it has nothing to invalidate.
        if (alreadyBuilt) {
            invalidateDemandSuppliedBy(worldId, projectId)
        }
        CacheManager.onProjectCreated(worldId, projectId)
        bus.publish(IdeaImported(worldId, user.id, Instant.now(), ideaId, reviewedIdea.name))
        projectId
    }
}

/**
 * Replaces an idea's requirements with the ones the review page sent back.
 *
 * The whole list arrives in one `materials` field (MCO-315); rows the user struck are carried
 * too and dropped here. Ids are checked against the world's catalog rather than against the
 * idea, so a submission cannot smuggle in an item the idea never listed.
 *
 * There is deliberately no "not reviewed, use the idea's list" fallback. A form with every
 * row unchecked and a bare direct post are near-identical, so a fallback would silently
 * import everything at exactly the moment the user asked for nothing. Import goes through
 * the review screen; a submission with no rows — or with no list at all — is refused.
 *
 * [alreadyBuilt] (MCO-457) drops the list instead of reading it: the farm is standing in the
 * world already, so there is nothing to gather and the review screen said so. That is not the
 * fallback this step refuses to have — the fallback would import *more* than was asked for,
 * where this imports nothing at all, which is the direction it is safe to be wrong in. The
 * name is still taken from the form, since renaming is orthogonal to whether it is built.
 */
internal data class ApplyReviewedRequirementsStep(
    val submitted: Parameters,
    val availableItems: List<Item>,
    val alreadyBuilt: Boolean = false,
) : Step<IdeaForImport, AppFailure, IdeaForImport> {

    override suspend fun process(input: IdeaForImport): Result<AppFailure, IdeaForImport> {
        if (alreadyBuilt) {
            return Result.success(input.copy(name = submittedName(input.name), requirements = emptyMap()))
        }

        val submittedRows = when (
            val decoded = ReviewedMaterialsCodec.decode(submitted[ReviewedMaterialsCodec.FIELD])
        ) {
            is Result.Failure -> return Result.Failure(decoded.error)
            is Result.Success -> decoded.value
        }

        val byId = availableItems.associateBy { it.id }
        val errors = mutableListOf<ValidationFailure>()
        val requirements = mutableMapOf<Item, Int>()

        submittedRows.forEach { row ->
            if (!row.included) return@forEach
            // The review screen never offers air, so this only catches a hand-rolled post —
            // but "air is never a material" is worth enforcing where the list becomes final.
            if (row.itemId in NON_MATERIAL_FILL) return@forEach

            val item = byId[row.itemId]
            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("materials", "Unknown item: ${row.itemId}"))
                return@forEach
            }
            // Summed, not assigned — see the same note in ValidateReviewedMaterialsStep. The
            // idea door has no regions today, but it posts to the same codec and the two
            // readers must not disagree about what a repeated id means.
            requirements[item] = (requirements[item] ?: 0) + row.amount
        }

        if (errors.isNotEmpty()) return Result.failure(AppFailure.ValidationError(errors))
        if (requirements.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("materials", "Keep at least one material to import")
            )
        }

        return Result.success(input.copy(name = submittedName(input.name), requirements = requirements))
    }

    private fun submittedName(fallback: String): String =
        submitted["name"]?.trim()?.takeIf { it.isNotBlank() }?.take(100) ?: fallback
}

private object ValidateVersionRangeStep : Step<Pair<Int, Int>, AppFailure, Pair<Int, Int>> {
    override suspend fun process(input: Pair<Int, Int>): Result<AppFailure, Pair<Int, Int>> {
        val worldVersion = DatabaseSteps.query<Int, MinecraftVersion>(
            sql = SafeSQL.select("SELECT version FROM world WHERE id = ?"),
            parameterSetter = { statement, worldId ->
                statement.setInt(1, worldId)
            },
            resultMapper = { resultSet ->
                resultSet.next()
                MinecraftVersion.fromString(resultSet.getString("version"))
            }
        ).process(input.first)

        val ideaVersion = DatabaseSteps.query<Int, MinecraftVersionRange>(
            sql = SafeSQL.select("SELECT minecraft_version_range FROM ideas WHERE id = ?"),
            parameterSetter = { statement, ideaId ->
                statement.setInt(1, ideaId)
            },
            resultMapper = { resultSet ->
                resultSet.next()
                Json.decodeFromString(MinecraftVersionRange.serializer(), resultSet.getString("minecraft_version_range"))
            }
        ).process(input.second)

        if (worldVersion is Result.Failure) {
            return worldVersion
        }

        if (ideaVersion is Result.Failure) {
            return ideaVersion
        }

        return if (ideaVersion.getOrNull()!!.contains(worldVersion.getOrNull()!!)) {
            Result.Success(input)
        } else {
            Result.Failure(AppFailure.customValidationError("idea", "Idea is not compatible with the world's Minecraft version"))
        }
    }
}

data class BasicIdeaInfo(
    val id: Int,
    val name: String,
    val description: String,
    val category: IdeaCategory,
    val productionRate: Map<String, Int>
)

private val GetIdeaForImportStep = DatabaseSteps.transaction { connection ->
    object : Step<ImportSelection, AppFailure.DatabaseError, Triple<BasicIdeaInfo, Map<String, Int>, BuildTimeChoice>> {
        override suspend fun process(
            selection: ImportSelection,
        ): Result<AppFailure.DatabaseError, Triple<BasicIdeaInfo, Map<String, Int>, BuildTimeChoice>> {
            val input = selection.ideaId
            val ideaInfo = DatabaseSteps.query<Int, BasicIdeaInfo>(
                sql = SafeSQL.select("""
                    SELECT id, name, description, category
                    FROM ideas
                    WHERE id = ?
                """.trimIndent()),
                parameterSetter = { statement, id ->
                    statement.setInt(1, id)
                },
                resultMapper = { resultSet ->
                    resultSet.next()
                    BasicIdeaInfo(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        IdeaCategory.valueOf(resultSet.getString("category")),
                        // Read from the idea's own production tables below, not from category JSON.
                        emptyMap()
                    )
                },
                transactionConnection = connection
            ).process(input)

            if (ideaInfo is Result.Failure) {
                return Result.Failure(ideaInfo.error)
            }

            val requirementInfo = DatabaseSteps.query<Int, Map<String, Int>>(
                // Base list only — a build-time mode's own list is chosen at import and read from
                // the modes (MCO-463, V2_61_0). Without the filter an idea with variants would
                // import the sum of all of them.
                sql = SafeSQL.select(
                    "SELECT item_id, quantity FROM idea_item_requirements WHERE idea_id = ? AND mode_id IS NULL"
                ),
                parameterSetter = { statement, ideaId ->
                    statement.setInt(1, ideaId)
                },
                resultMapper = {
                    buildMap {
                        while (it.next()) {
                            val itemId = it.getString("item_id")
                            val quantity = it.getInt("quantity")
                            this[itemId] = quantity
                        }
                    }
                }
            ).process(input)

            if (requirementInfo is Result.Failure) {
                return Result.Failure(requirementInfo.error)
            }

            // Productions live in their own tables now (MCO-412). The idea carries modes; the
            // project records the rates of the one it will be run in.
            val modes = GetIdeaProductionModesStep(input).process(Unit)
            if (modes is Result.Failure) {
                return Result.Failure(modes.error)
            }
            val allModes = modes.getOrNull()!!
            val buildTime = allModes.buildTimeModes()

            // Which variant is being imported. The author's first is the default rather than the
            // fastest or the largest: `position` is explicitly the author's ordering and not a
            // ranking, and defaulting to the biggest producer would quietly commit someone to the
            // 4-module build and its 4x material bill. The review screen shows the choice either
            // way, so a neutral, predictable default beats a clever one.
            val chosen = buildTime.firstOrNull { it.name == selection.buildTimeMode }
                ?: buildTime.firstOrNull()

            // A build-time variant's own list *replaces* the base list (V2_61_0), so this is a
            // choice between lists rather than a merge.
            val requirements = if (chosen != null && chosen.requirements.isNotEmpty()) {
                chosen.requirements
            } else {
                requirementInfo.getOrNull()!!
            }

            val info = ideaInfo.getOrNull()!!.copy(
                productionRate = ratesForImport(allModes, chosen?.name),
            )
            return Result.Success(
                Triple(
                    info,
                    requirements,
                    BuildTimeChoice(buildTime.map { it.name }, chosen?.name),
                )
            )
        }
    }
}

/** The build-time variants an idea offers, and which one this import is reading. */
internal data class BuildTimeChoice(
    val available: List<String> = emptyList(),
    val chosen: String? = null,
)

/**
 * The rates an imported farm project should record, chosen from the idea's modes.
 *
 * ## Flattening is correct for a build-time mode, and wrong for a runtime one
 *
 * This was filed as wholly interim (Even, 2026-08-16) on the reading that a mode is always a
 * *runtime* choice — you might run the fortress farm skeletons-only this week and everything-on
 * next, and flattening means re-typing rates to switch. MCO-439 finding 1 showed both kinds exist,
 * and split the issue in two:
 *
 *  - **Build-time** (MCO-463): flattening here is right. You pick 4-modules-with-storage once, and
 *    the project's requirements and rates are both fixed from that one variant. Passing
 *    [chosenModeName] is how the review screen's choice reaches the project.
 *  - **Runtime** (MCO-413): still the wrong shape, and still to be fixed. Those modes belong on the
 *    project with one active, so switching does not re-type anything.
 *
 * When [chosenModeName] names a mode, that mode's rates are used **even if it has none**. A
 * build-time variant nobody timed honestly produces an unmeasured amount; falling back to a
 * sibling's rate would attribute one variant's throughput to another, which is the drift this
 * whole issue exists to stop.
 *
 * With no choice given, the mode producing the most across its items wins: an import that silently
 * picked the slowest would under-promise supply for no reason the user could see.
 */
internal fun ratesForImport(modes: List<IdeaProductionMode>, chosenModeName: String? = null): Map<String, Int> {
    if (modes.isEmpty()) return emptyMap()
    val chosen = chosenModeName?.let { name -> modes.firstOrNull { it.name == name } }
        ?: modes.maxByOrNull { mode -> mode.rates.values.sumOf { (it ?: 0).toLong() } }
    // An unmeasured rate becomes 0, which is what project_productions already means by it —
    // ProductionPanel prints "rate unknown" for exactly this. The two sides represent the same
    // fact differently until MCO-413 unifies them, and this is the one place that maps between.
    return chosen?.rates.orEmpty().mapValues { (_, rate) -> rate ?: 0 }
}


/**
 * Turns an idea's recorded ids into the items this world would actually gather.
 *
 * The requirement side runs the **same** placed-cell resolution the schematic door does
 * (MCO-308): an idea captured from a schematic records placed block-state ids, and taking
 * them verbatim put rows on the gathering list that no source produces — 592 `Redstone Wire
 * (Block)`, which MCO-305's warning strip then had to call creative-only. It is not; it is
 * 592 redstone. Resolving here rather than at capture time is deliberate: the tables resolve
 * *against a version catalog*, and an idea spans a version range, so the same idea honestly
 * resolves differently in two worlds.
 *
 * An id nothing claims is still a validation error, unlike the schematic door which drops it.
 * The version range was already checked by [ValidateVersionRangeStep], so reaching here with
 * an id outside the catalog means the idea and the world genuinely disagree.
 */
internal data class ValidateItemIdsStep(val availableIds: List<Item>) :
    Step<Triple<BasicIdeaInfo, Map<String, Int>, BuildTimeChoice>, AppFailure, IdeaForImport> {
    override suspend fun process(
        input: Triple<BasicIdeaInfo, Map<String, Int>, BuildTimeChoice>,
    ): Result<AppFailure, IdeaForImport> {
        val (ideaInfo, requirements, buildTime) = input
        val mappedProduction = mutableMapOf<Item, Int>()
        val unrecordable = mutableListOf<String>()
        val errors = mutableListOf<ValidationFailure>()

        // Air and the other non-materials are dropped, not reported (MCO-305) — an idea
        // recorded from a schematic can carry millions of air cells, and nobody has ever
        // wanted them on a gathering list.
        val resolved = resolvePlacedCells(requirements, availableIds.associateBy { it.id })
        val mappedRequirements = resolved.requirements.toMap()
        resolved.unknown.forEach { itemId ->
            errors.add(ValidationFailure.CustomValidation("requirements", "Item ID $itemId is not available in the world version"))
        }

        // An unknown *production* id is reported, not refused (MCO-456). It used to be a
        // validation error, which failed the whole import — and the review GET with it — over a
        // field that screen does not have, so there was nothing the user could do but give up on
        // the idea. What it produces is the author's claim about their farm, not work this world
        // is agreeing to do: the honest handling is to record what this version knows about, say
        // what was left out, and let the import through.
        for ((itemId, amount) in ideaInfo.productionRate) {
            val item = availableIds.find { it.id == itemId }

            if (item == null) {
                unrecordable.add(itemId)
            } else {
                mappedProduction[item] = amount
            }
        }

        return if (errors.isEmpty()) {
            Result.Success(
                IdeaForImport(
                    id = ideaInfo.id,
                    name = ideaInfo.name,
                    description = ideaInfo.description,
                    category = ideaInfo.category,
                    requirements = mappedRequirements,
                    production = mappedProduction,
                    placedCounts = resolved.placedCounts,
                    unrecordableProductions = unrecordable.sorted(),
                    buildTimeModes = buildTime.available,
                    chosenBuildTimeMode = buildTime.chosen,
                )
            )
        } else {
            Result.Failure(AppFailure.ValidationError(errors))
        }
    }
}

data class CreateDependencyInput(
    val projectId: Int,
    val taskId: Int,
    val dependsOnProjectId: Int
)

/**
 * Writes the project the idea becomes.
 *
 * [alreadyBuilt] (MCO-457) is the same fact MCO-298's `RecordExistingFarmPipeline` records for a
 * farm with no idea behind it: the build is standing in the world, so it enters operational
 * (`COMPLETED` + `DONE`) with its productions and **no gathering list at all**. Under MCO-287's
 * anchor decision DONE *is* the producing condition, so that is what makes it supply other
 * projects' plans ([app.mcorg.pipeline.resources.GetWorldFarmSuppliesStep]) instead of merely
 * promising to via `GetWorldPlannedFarmsStep`. Like that pipeline, it bypasses
 * [app.mcorg.domain.model.project.ProjectState.allowedTransitions] on purpose — there is no
 * `PENDING -> DONE` edge because a pre-existing build was never planned here.
 *
 * It stays this step rather than routing through `CreateExistingFarmStep`: that one writes no
 * `project_idea_id` and knows nothing about `forTask`, so reusing it would drop the link back to
 * the idea — the thing that makes this door worth having over the MCO-298 form.
 */
private data class CreateProjectFromIdeaStep(
    val worldId: Int,
    val taskId: Int?,
    val alreadyBuilt: Boolean = false,
) : Step<IdeaForImport, AppFailure.DatabaseError, Int> {

    // Two literals rather than a bound parameter: the lifecycle is this step's own choice, not
    // anything the request carries, and SafeSQL takes a constant.
    private val insertProject = if (alreadyBuilt) {
        SafeSQL.insert("""
            INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension, project_idea_id)
            VALUES (?, ?, ?, ?, 'COMPLETED', 'DONE', NULL, NULL, NULL, NULL, ?)
            RETURNING id
        """.trimIndent())
    } else {
        SafeSQL.insert("""
            INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension, project_idea_id)
            VALUES (?, ?, ?, ?, 'RESOURCE_GATHERING', 'ACTIVE', NULL, NULL, NULL, NULL, ?)
            RETURNING id
        """.trimIndent())
    }

    override suspend fun process(input: IdeaForImport): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction { connection ->
            object : Step<IdeaForImport, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: IdeaForImport): Result<AppFailure.DatabaseError, Int> {
                    val projectIdResult = DatabaseSteps.update<IdeaForImport>(
                        sql = insertProject,
                        parameterSetter = { statement, idea ->
                            statement.setInt(1, worldId)
                            statement.setString(2, idea.name)
                            statement.setString(3, idea.description)
                            statement.setString(4, idea.category.toProjectType().name)
                            statement.setInt(5, idea.id)
                        },
                        transactionConnection = connection
                    ).process(input)

                    if (projectIdResult is Result.Failure) {
                        return Result.Failure(projectIdResult.error)
                    }

                    val projectId = projectIdResult.getOrNull()!!

                    // Empty for an already-built import, and batching zero rows is a wasted
                    // round trip either way.
                    if (input.requirements.isNotEmpty()) {
                        val reqs = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
                            SafeSQL.insert("""
                                INSERT INTO resource_gathering
                                    (project_id, name, required, item_id)
                                    values (?, ?, ?, ?)
                            """.trimIndent()),
                            parameterSetter = { statement, idea ->
                                statement.setInt(1, projectId)
                                statement.setString(2, idea.first.name)
                                statement.setInt(3, idea.second)
                                statement.setString(4, idea.first.id)
                            },
                            transactionConnection = connection
                        ).process(input.requirements.toList())

                        if (reqs is Result.Failure) {
                            return Result.Failure(reqs.error)
                        }
                    }

                    val production = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
                        SafeSQL.insert("""
                            INSERT INTO project_productions
                                (project_id, name, item_id, rate_per_hour)
                                values (?, ?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, idea ->
                            statement.setInt(1, projectId)
                            statement.setString(2, idea.first.name)
                            statement.setString(3, idea.first.id)
                            statement.setInt(4, idea.second)
                        },
                        transactionConnection = connection
                    ).process(input.production.toList())

                    if (production is Result.Failure) {
                        return Result.Failure(production.error)
                    }

                    if (taskId != null) {
                        val projectOfTaskId = DatabaseSteps.query<Int, Int>(
                            sql = SafeSQL.select("SELECT project_id FROM resource_gathering WHERE id = ?"),
                            parameterSetter = { statement, tId ->
                                statement.setInt(1, tId)
                            },
                            resultMapper = { resultSet ->
                                resultSet.next()
                                resultSet.getInt("project_id")
                            },
                            transactionConnection = connection
                        ).process(taskId)

                        if (projectOfTaskId is Result.Failure) {
                            return projectOfTaskId
                        }
                        val dependencyResult = DatabaseSteps.update<CreateDependencyInput>(
                            sql = SafeSQL.insert("""
                                INSERT INTO project_dependencies (project_id, depends_on_project_id, tasks_depending_on_dependency_project) VALUES (?, ?, ?)
                            """.trimIndent()),
                            parameterSetter = { statement, input ->
                                statement.setInt(1, input.projectId)
                                statement.setInt(2, input.dependsOnProjectId)
                                statement.setArray(3, statement.connection.createArrayOf("INTEGER", arrayOf(input.taskId)))
                            },
                            transactionConnection = connection
                        ).process(CreateDependencyInput(
                            projectId = projectOfTaskId.getOrNull()!!,
                            taskId = taskId,
                            dependsOnProjectId = projectId
                        ))

                        if (dependencyResult is Result.Failure) {
                            return dependencyResult
                        }
                    }

                    return Result.Success(projectId)
                }
            }
        }.process(input)
    }

    private fun IdeaCategory.toProjectType(): ProjectType {
        return when (this) {
            IdeaCategory.BUILD -> ProjectType.BUILDING
            IdeaCategory.FARM -> ProjectType.FARMING
            IdeaCategory.STORAGE -> ProjectType.TECHNICAL
            IdeaCategory.CART_TECH -> ProjectType.TECHNICAL
            IdeaCategory.TNT -> ProjectType.TECHNICAL
            IdeaCategory.SLIMESTONE -> ProjectType.REDSTONE
            IdeaCategory.OTHER -> ProjectType.DECORATION
        }
    }
}
