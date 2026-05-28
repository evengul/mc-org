package app.mcorg.pipeline.idea.draft

import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraftfiles.GetSupportedVersionsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.createwizard.DraftWizardStage
import app.mcorg.presentation.templated.idea.createwizard.draftWizardPage
import app.mcorg.presentation.templated.idea.createwizard.wizardProgressHtml
import app.mcorg.presentation.templated.idea.createwizard.wizardStageContent
import app.mcorg.presentation.templated.idea.draftListPage
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
                is DraftListOutcome.Redirect -> clientRedirect(outcome.url)
                is DraftListOutcome.ShowList -> respondHtml(draftListPage(user, outcome.drafts))
            }
        }
    ) {
        val draftList = GetDraftsStep(user.id).run(Unit)
        if (draftList.isEmpty()) {
            val draftId = CreateDraftStep(user.id).run(Unit)
            DraftListOutcome.Redirect("/ideas/drafts/$draftId/edit")
        } else {
            DraftListOutcome.ShowList(draftList)
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
        CreateDraftStep(user.id).run(Unit)
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
    val stageParam = parameters["stage"]?.let {
        runCatching { DraftWizardStage.valueOf(it) }.getOrNull()
    }

    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    handlePipeline(
        onSuccess = { draft ->
            val stage = stageParam
                ?: runCatching { DraftWizardStage.valueOf(draft.currentStage) }.getOrDefault(DraftWizardStage.BASIC_INFO)
            respondHtml(draftWizardPage(user, draft, stage, supportedVersions))
        }
    ) {
        GetDraftStep().run(GetDraftInput(draftId, user.id))
    }
}

/**
 * POST /ideas/drafts/:draftId/stage
 * Saves current stage data into JSONB, advances to next stage, returns fragment.
 */
suspend fun ApplicationCall.handleUpdateDraftStage() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
    }
    val params = receiveParameters()
    val currentStageName = params["currentStage"] ?: "BASIC_INFO"
    val currentStage = runCatching { DraftWizardStage.valueOf(currentStageName) }
        .getOrDefault(DraftWizardStage.BASIC_INFO)
    val nextStage = params["targetStage"]
        ?.let { runCatching { DraftWizardStage.valueOf(it) }.getOrNull() }
        ?: (currentStage.next() ?: DraftWizardStage.REVIEW)

    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()
    val stageJson = buildStageJson(currentStage, params)
    val movingForward = nextStage.ordinal > currentStage.ordinal
    val validationErrors = if (movingForward) {
        (ValidateStageStep.process(ValidateStageInput(currentStage, params)) as? Result.Success)?.value ?: emptyList()
    } else {
        emptyList()
    }

    if (validationErrors.isNotEmpty()) {
        val draft = GetDraftStep().process(GetDraftInput(draftId, user.id)).getOrNull()
        if (draft != null) {
            val previewDraft = mergeStageIntoDraft(draft, stageJson)
            response.headers.append("HX-Retarget", "#wizard-stage")
            response.headers.append("HX-Reswap", "outerHTML")
            respondHtml(
                createHTML().div {
                    id = "wizard-stage"
                    wizardStageContent(previewDraft, currentStage, supportedVersions, validationErrors)
                },
                HttpStatusCode.UnprocessableEntity
            )
        } else {
            respondHtml("<p>Draft not found</p>", HttpStatusCode.NotFound)
        }
        return
    }

    handlePipeline(
        onSuccess = { draft ->
            response.headers.append("HX-Push-Url", "/ideas/drafts/$draftId/edit?stage=${nextStage.name}")
            val stageHtml = createHTML().div {
                id = "wizard-stage"
                wizardStageContent(draft, nextStage, supportedVersions)
            }
            val progressHtml = wizardProgressHtml(draftId, nextStage, oob = true)
            val titleHtml = createHTML().h1("wizard-title") {
                id = "wizard-title"
                attributes["hx-swap-oob"] = "true"
                +"${if (draft.name != null) "\"${draft.name}\"" else "New Draft"}"
            }
            respondHtml(stageHtml + progressHtml + titleHtml)
        }
    ) {
        UpdateDraftStep().run(UpdateDraftInput(draftId, user.id, stageJson, nextStage.name))
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
 * Validate + create idea + delete draft + redirect.
 */
suspend fun ApplicationCall.handlePublishDraft() {
    val user = getUser()
    val draftId = parameters["draftId"]?.toIntOrNull() ?: run {
        respondHtml("<p>Invalid draft ID</p>"); return
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
 * Converts form params for a given wizard stage into a JSON string suitable for JSONB merge.
 */
private fun buildStageJson(stage: DraftWizardStage, params: Parameters): String {
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
    is CategoryField.BooleanField ->
        CategoryValue.BooleanValue(params[paramPrefix] == "true")
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
    is CategoryField.TypedMapField -> null
}

private fun mergeStageIntoDraft(draft: app.mcorg.domain.model.idea.IdeaDraft, stageJson: String): app.mcorg.domain.model.idea.IdeaDraft {
    val existing = runCatching { Json.parseToJsonElement(draft.data).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    val incoming = runCatching { Json.parseToJsonElement(stageJson).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    val merged = JsonObject(existing + incoming)
    return draft.copy(data = merged.toString())
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
