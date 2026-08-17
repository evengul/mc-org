package app.mcorg.pipeline.idea.draft

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.createwizard.DraftWizardStage
import app.mcorg.presentation.templated.idea.createwizard.draftFormFragment
import app.mcorg.presentation.templated.idea.createwizard.draftFormPage
import app.mcorg.presentation.templated.idea.draftListPage
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.redirectClientOrBrowser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.stream.createHTML
import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.failure.ValidationFailure
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * GET /ideas/create
 * Shows draft list if user has drafts; creates a new draft and redirects if none.
 */
suspend fun ApplicationCall.handleGetDraftList() {
    val user = getUser()

    handlePipeline(
        onSuccess = { outcome ->
            when (outcome) {
                // Reached by a plain link from the hub, so this must be a real redirect for a
                // browser and an HX-Redirect for HTMX — not the HTMX-only form (MCO-310).
                is DraftListOutcome.Redirect -> redirectClientOrBrowser(outcome.url)
                is DraftListOutcome.ShowList -> respondHtml(draftListPage(user, outcome.drafts))
            }
        }
    ) {
        val draftList = GetDraftsStep(user.id).run(Unit)
        val untouched = draftList.firstOrNull { it.isUntouched }
        when {
            draftList.isEmpty() -> DraftListOutcome.Redirect("/ideas/drafts/${CreateDraftStep(user.id).run(Unit)}/edit")
            // Every blank draft is the same blank draft — send them back to it rather than
            // showing a list of indistinguishable "Untitled Draft" rows.
            draftList.all { it.isUntouched } -> DraftListOutcome.Redirect("/ideas/drafts/${untouched!!.id}/edit")
            else -> DraftListOutcome.ShowList(draftList)
        }
    }
}

private sealed interface DraftListOutcome {
    data class Redirect(val url: String) : DraftListOutcome
    data class ShowList(val drafts: List<app.mcorg.domain.model.idea.IdeaDraft>) : DraftListOutcome
}

/**
 * POST /ideas/create
 * Creates a new draft and redirects to wizard.
 */
suspend fun ApplicationCall.handleCreateDraft() {
    val user = getUser()

    handlePipeline(
        onSuccess = { draftId ->
            clientRedirect("/ideas/drafts/$draftId/edit")
        }
    ) {
        // Reuse a blank draft if one is already lying around, so repeated "New Draft" clicks do
        // not stack up identical empty rows.
        GetDraftsStep(user.id).run(Unit).firstOrNull { it.isUntouched }?.id
            ?: CreateDraftStep(user.id).run(Unit)
    }
}

/**
 * GET /ideas/drafts/:draftId/edit
 * Shows wizard at the draft's current_stage (or query param ?stage=).
 */
suspend fun ApplicationCall.handleGetDraftWizard() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
    }

    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    handlePipeline(
        onSuccess = { draft ->
            respondHtml(draftFormPage(user, draft, supportedVersions))
        }
    ) {
        GetDraftStep().run(GetDraftInput(draftId, user.id))
    }
}

/**
 * DELETE /ideas/drafts/:draftId
 * Discards draft (ownership enforced in SQL).
 * If the draft is an edit of a published idea (source_idea_id set), restores the idea and redirects to it.
 */
suspend fun ApplicationCall.handleDeleteDraft() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
    }

    val sourceIdeaId = GetDraftStep().process(GetDraftInput(draftId, user.id)).getOrNull()?.sourceIdeaId

    handlePipeline(
        onSuccess = {
            if (sourceIdeaId != null) {
                CacheManager.onIdeaCreated(sourceIdeaId)
                clientRedirect("/ideas/$sourceIdeaId")
            } else {
                respondHtml("")
            }
        }
    ) {
        DeleteDraftStep().run(DeleteDraftInput(draftId, user.id))
        if (sourceIdeaId != null) {
            DatabaseSteps.update<Int>(
                sql = SafeSQL.update("UPDATE ideas SET is_active = TRUE WHERE id = ?"),
                parameterSetter = { stmt, id -> stmt.setInt(1, id) }
            ).run(sourceIdeaId)
        }
    }
}

/**
 * POST /ideas/drafts/:draftId/publish
 *
 * The single-page form (MCO-310) posts every field at once, so this saves what was submitted before
 * validating it — there is no longer a per-stage save that ran first. On failure it re-renders the
 * form in place with the errors and the user's own input still in it.
 *
 * Despite the route name this produces a *private* idea; publishing to the hub is a separate,
 * privileged step (MCO-291).
 */
suspend fun ApplicationCall.handlePublishDraft() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
    }
    // A bodyless POST means "publish what is already stored" — merging empty params would clobber
    // fields the draft already holds, so only save when a form was actually submitted.
    val params = runCatching { receiveParameters() }.getOrNull()

    if (params != null) {
        val saved = UpdateDraftStep().process(
            UpdateDraftInput(draftId, user.id, buildFormJson(params, user.minecraftUsername), DraftWizardStage.REVIEW.name)
        )
        if (saved is Result.Failure) {
            respondHtml("<p>Draft not found</p>", HttpStatusCode.NotFound)
            return
        }
    }

    val validationErrors = params?.let { submitted ->
        DraftWizardStage.entries.flatMap { stage ->
            (ValidateStageStep.process(ValidateStageInput(stage, submitted)) as? Result.Success)?.value ?: emptyList()
        }
    } ?: emptyList()

    if (validationErrors.isNotEmpty()) {
        val draft = GetDraftStep().process(GetDraftInput(draftId, user.id)).getOrNull()
        if (draft == null) {
            respondHtml("<p>Draft not found</p>", HttpStatusCode.NotFound)
            return
        }
        respondHtml(
            draftFormFragment(
                draft = draft,
                supportedVersions = GetSupportedVersionsStep.getSupportedVersions(),
                errors = validationErrors,
                defaultAuthorName = user.minecraftUsername,
            ),
            HttpStatusCode.UnprocessableEntity
        )
        return
    }

    handlePipeline(
        onSuccess = { ideaId ->
            clientRedirect("/ideas/$ideaId")
        }
    ) {
        val draft = GetDraftStep().run(GetDraftInput(draftId, user.id))
        PublishDraftStep().run(PublishDraftInput(draft, user.id))
    }
}

/**
 * POST /ideas/drafts/:draftId/save
 *
 * Keeps what has been typed without validating or turning it into an idea, so a half-finished
 * design survives being interrupted. Nothing is required here — that is the point.
 */
suspend fun ApplicationCall.handleSaveDraftForm() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
    }
    val params = receiveParameters()

    handlePipeline(
        onSuccess = { redirectClientOrBrowser("/ideas/create") }
    ) {
        UpdateDraftStep().run(
            UpdateDraftInput(draftId, user.id, buildFormJson(params, user.minecraftUsername), DraftWizardStage.REVIEW.name)
        )
    }
}

/**
 * Every field on the single-page form, merged into one draft-shaped JSON object. Reuses the
 * per-stage builders rather than duplicating their parsing — the stages are gone from the UI but
 * remain a useful grouping for parsing and validation.
 */
private fun buildFormJson(params: Parameters, fallbackAuthorName: String): String {
    val effective = if (params["authorName"].isNullOrBlank()) {
        // "Credited to you" unless you say otherwise — the author field is optional on the form.
        ParametersBuilder().apply {
            params.names().filter { it != "authorName" }.forEach { name ->
                params.getAll(name)?.forEach { append(name, it) }
            }
            append("authorName", fallbackAuthorName)
        }.build()
    } else {
        params
    }

    val merged = DraftWizardStage.entries
        .map { Json.parseToJsonElement(buildStageJson(it, effective)).jsonObject }
        .fold(mutableMapOf<String, JsonElement>()) { acc, obj -> acc.apply { putAll(obj) } }
    return JsonObject(merged).toString()
}

/**
 * Converts form params for a given wizard stage into a JSON string suitable for JSONB merge.
 */
/** Internal rather than private so the per-stage parsing can be tested directly. */
internal fun buildStageJson(stage: DraftWizardStage, params: Parameters): String {
    return when (stage) {
        DraftWizardStage.BASIC_INFO -> buildJsonObject {
            params["name"]?.let { put("name", it) }
            params["description"]?.let { put("description", it) }
            params["difficulty"]?.let { put("difficulty", it) }
        }.toString()

        DraftWizardStage.AUTHOR_INFO -> {
            val authorType = params["authorType"] ?: "single"
            val author: Author = when (authorType) {
                "team" -> {
                    val indices = params.names()
                        .mapNotNull { Regex("""teamMembers\[(\d+)]\[name]""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                        .sorted()
                    val members = indices.mapIndexedNotNull { order, index ->
                        val name = params["teamMembers[$index][name]"]
                        if (name.isNullOrBlank()) null
                        else Author.TeamAuthor(
                            name = name,
                            order = order,
                            role = params["teamMembers[$index][role]"] ?: "",
                            contributions = params["teamMembers[$index][contributions]"]
                                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                                ?: emptyList()
                        )
                    }
                    if (members.isEmpty()) Author.SingleAuthor(params["authorName"] ?: "")
                    else Author.Team(members)
                }
                else -> Author.SingleAuthor(params["authorName"] ?: "")
            }
            buildJsonObject {
                put("author", Json.encodeToJsonElement(Author.serializer(), author))
            }.toString()
        }

        DraftWizardStage.VERSION_COMPATIBILITY -> {
            val rangeType = params["versionRangeType"] ?: "unbounded"
            val versionFrom = params["versionFrom"]?.let { runCatching { MinecraftVersion.Release.fromString(it) }.getOrNull() }
            val versionTo = params["versionTo"]?.let { runCatching { MinecraftVersion.Release.fromString(it) }.getOrNull() }
            val versionRange: MinecraftVersionRange = when (rangeType) {
                "lowerBounded" -> versionFrom?.let { MinecraftVersionRange.LowerBounded(it) } ?: MinecraftVersionRange.Unbounded
                "upperBounded" -> versionTo?.let { MinecraftVersionRange.UpperBounded(it) } ?: MinecraftVersionRange.Unbounded
                "bounded" -> if (versionFrom != null && versionTo != null) MinecraftVersionRange.Bounded(versionFrom, versionTo) else MinecraftVersionRange.Unbounded
                else -> MinecraftVersionRange.Unbounded
            }
            buildJsonObject {
                put("versionRange", Json.encodeToJsonElement(MinecraftVersionRange.serializer(), versionRange))
            }.toString()
        }

        DraftWizardStage.ITEM_REQUIREMENTS -> buildJsonObject {
            val items = params.names()
                .filter { it.startsWith("itemRequirements[") }
                .associate { key ->
                    val itemId = key.removePrefix("itemRequirements[").removeSuffix("]")
                    itemId to (params[key]?.toIntOrNull() ?: 0)
                }
            if (items.isNotEmpty()) {
                putJsonObject("itemRequirements") {
                    items.forEach { (k, v) -> put(k, v) }
                }
            }
        }.toString()

        // productionMode[i][name] names a mode; productionRate[i][<item id>] is one of its rates.
        // The index groups them and is positional only — it is the order the author added them in,
        // and it is re-assigned on every save, so nothing may reference it.
        DraftWizardStage.PRODUCTIONS -> buildJsonObject {
            val names = params.names()
                .filter { it.startsWith("productionMode[") && it.endsWith("][name]") }
                .associate { key ->
                    key.removePrefix("productionMode[").removeSuffix("][name]") to (params[key] ?: "")
                }
            val ratesByMode = params.names()
                .filter { it.startsWith("productionRate[") }
                .mapNotNull { key ->
                    val body = key.removePrefix("productionRate[")
                    val index = body.substringBefore("]", missingDelimiterValue = "")
                    val itemId = body.substringAfter("][", missingDelimiterValue = "").removeSuffix("]")
                    val raw = params[key]?.trim().orEmpty()
                    // Blank is "produces this, never measured how fast" — a small private bamboo
                    // farm still tells you it makes bamboo. Only a *malformed* rate is discarded,
                    // and the item with it, since there is nothing to trust in the row.
                    val rate = if (raw.isEmpty()) null else raw.toIntOrNull()
                    val malformed = raw.isNotEmpty() && (rate == null || rate < 0)
                    if (index.isBlank() || itemId.isBlank() || malformed) null
                    else Triple(index, itemId, rate)
                }
                .groupBy({ it.first }, { it.second to it.third })

            // A mode with no rates says nothing about the farm, so it does not survive the form.
            val modes = ratesByMode.keys.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            if (modes.isNotEmpty()) {
                putJsonArray("productionModes") {
                    modes.forEach { index ->
                        addJsonObject {
                            put("name", names[index] ?: "")
                            putJsonObject("rates") {
                                ratesByMode[index].orEmpty().forEach { (itemId, rate) ->
                                    if (rate == null) put(itemId, JsonNull) else put(itemId, rate)
                                }
                            }
                        }
                    }
                }
            }
        }.toString()

        DraftWizardStage.CATEGORY_FIELDS -> {
            val categoryStr = params["category"]
            val category = categoryStr?.let { runCatching { IdeaCategory.valueOf(it) }.getOrNull() }
            val schema = category?.let { IdeaCategorySchemas.getSchema(it) }
            buildJsonObject {
                categoryStr?.let { put("category", it) }
                if (schema != null) {
                    val categoryData = buildCategoryData(schema.fields, params)
                    if (categoryData.isNotEmpty()) {
                        put("categoryData", Json.encodeToJsonElement(
                            MapSerializer(String.serializer(), CategoryValue.serializer()),
                            categoryData
                        ))
                    }
                }
            }.toString()
        }

        DraftWizardStage.REVIEW -> "{}"
    }
}

private fun buildCategoryData(fields: List<CategoryField>, params: Parameters): Map<String, CategoryValue> {
    val result = mutableMapOf<String, CategoryValue>()
    fields.forEach { field ->
        extractCategoryValue(field, params, "categoryData.${field.key}")?.let {
            result[field.key] = it
        }
    }
    return result
}

private fun extractCategoryValue(field: CategoryField, params: Parameters, paramPrefix: String): CategoryValue? = when (field) {
    is CategoryField.Text, is CategoryField.Select ->
        params[paramPrefix]?.takeIf { it.isNotBlank() }?.let { CategoryValue.TextValue(it) }
    is CategoryField.Number, is CategoryField.Rate, is CategoryField.Percentage ->
        params[paramPrefix]?.toIntOrNull()?.let { CategoryValue.IntValue(it) }
    // Only record a ticked box. An unticked one posts nothing, and storing that as an explicit
    // "No" put three rows the author never asserted onto every minimally-filled idea's detail
    // page. Absence already means false, since category data is rebuilt from the form on save.
    is CategoryField.BooleanField ->
        CategoryValue.BooleanValue(true).takeIf { params[paramPrefix] == "true" }
    is CategoryField.MultiSelect ->
        params.getAll("$paramPrefix[]")?.toSet()?.takeIf { it.isNotEmpty() }
            ?.let { CategoryValue.MultiSelectValue(it) }
    is CategoryField.ListField ->
        params[paramPrefix]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?.toSet()?.takeIf { it.isNotEmpty() }?.let { CategoryValue.MultiSelectValue(it) }
    is CategoryField.StructField -> {
        val subMap = mutableMapOf<String, CategoryValue>()
        field.fields.forEach { subField ->
            extractCategoryValue(subField, params, "$paramPrefix.${subField.key}")
                ?.let { subMap[subField.key] = it }
        }
        if (subMap.isNotEmpty()) CategoryValue.MapValue(subMap) else null
    }
    /**
     * Rows arrive as parallel `key[]` / `value[]` arrays, matching the names
     * [CategoryField.getCompleteKey] renders. A row is kept only when both sides are filled —
     * the form always carries one blank row, and half a row is not an entry.
     *
     * Nested typed maps (a map whose value is itself a map) submit no `value[]` at this level,
     * so they fall out as empty. No schema uses one today; reintroducing one means recursing here.
     */
    is CategoryField.TypedMapField -> {
        val keys = params.getAll("$paramPrefix.key[]").orEmpty()
        val values = params.getAll("$paramPrefix.value[]").orEmpty()
        val entries = mutableMapOf<String, CategoryValue>()
        keys.forEachIndexed { index, key ->
            val raw = values.getOrNull(index)
            if (key.isNotBlank() && !raw.isNullOrBlank()) {
                val value = when (field.valueType) {
                    is CategoryField.Number, is CategoryField.Rate, is CategoryField.Percentage ->
                        raw.toIntOrNull()?.let { CategoryValue.IntValue(it) }
                    else -> CategoryValue.TextValue(raw)
                }
                value?.let { entries[key] = it }
            }
        }
        if (entries.isNotEmpty()) CategoryValue.MapValue(entries) else null
    }
}

fun ValidationFailure.toMessage(): String {
    val label = formatParamName(parameterName)
    return when (this) {
        is ValidationFailure.MissingParameter -> "$label is required."
        is ValidationFailure.InvalidFormat -> message ?: "$label has an invalid format."
        is ValidationFailure.InvalidLength -> when {
            minLength != null && maxLength != null -> "$label must be between $minLength and $maxLength characters."
            minLength != null -> "$label must be at least $minLength characters."
            else -> "$label must be at most $maxLength characters."
        }
        is ValidationFailure.InvalidValue -> "$label is not a valid value."
        is ValidationFailure.OutOfRange -> when {
            min != null && max != null -> "$label must be between $min and $max."
            min != null -> "$label must be at least $min."
            else -> "$label must be at most $max."
        }
        is ValidationFailure.CustomValidation -> message
    }
}

private fun formatParamName(raw: String): String {
    val clean = raw.removePrefix("categoryData.").removeSuffix("[]")
    return clean.split(".").joinToString(" › ") { segment ->
        segment
            .replace(Regex("([A-Z])"), " $1")
            .trim()
            .replaceFirstChar { it.uppercase() }
    }
}

/**
 * POST /ideas/{ideaId}/revert
 * Reverts a published idea to draft state for editing.
 * Only available to the idea creator or superadmin.
 */
suspend fun ApplicationCall.handleRevertIdeaToDraft() {
    val user = getUser()
    val ideaId = getIdeaId()

    // Ownership check at handler level (not inside the pipeline step)
    val idea = when (val r = GetIdeaStep.process(ideaId)) {
        is Result.Failure -> { respondHtml("<p>Idea not found</p>", HttpStatusCode.NotFound); return }
        is Result.Success -> r.value
    }
    if (idea.createdBy != user.id && !user.isSuperAdmin) {
        respondHtml("<p>Forbidden</p>", HttpStatusCode.Forbidden)
        return
    }

    handlePipeline(
        onSuccess = { draftId ->
            CacheManager.onIdeaDeleted(ideaId)
            clientRedirect("/ideas/drafts/$draftId/edit")
        }
    ) {
        RevertIdeaToDraftStep().run(RevertIdeaToDraftInput(ideaId, user.id))
    }
}
