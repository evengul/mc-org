package app.mcorg.presentation.utils

import app.mcorg.domain.*
import app.mcorg.domain.categorization.CategoryType
import app.mcorg.domain.categorization.SubCategory
import app.mcorg.domain.categorization.subtypes.*
import app.mcorg.presentation.entities.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*

suspend fun ApplicationCall.receiveCreateWorldRequest(): CreateWorldRequest {
    val data = receiveParameters()
    val worldName = data["worldName"] ?: throw IllegalArgumentException("worldName is required")
    val gameType = data["gameType"]?.toGameType() ?: GameType.JAVA
    val version = data["version"]?.toWorldVersion() ?: WorldVersion(21, 0)
    val isTechnical = data["isTechnical"] == "on"
    if (worldName.length < 3) throw IllegalArgumentException("worldName must be longer than 3 characters")
    return CreateWorldRequest(worldName, gameType, version, isTechnical)
}

suspend fun ApplicationCall.receiveAddUserRequest(): AddUserRequest {
    val data = receiveParameters()
    val username = data["username"] ?: throw IllegalArgumentException("username is required")
    return AddUserRequest(username)
}

suspend fun ApplicationCall.receiveAssignUserRequest(): AssignUserOrDeleteAssignmentRequest {
    val data = receiveParameters()
    val unsafeUserId = data["userId"]
    if (unsafeUserId == "-1") return DeleteAssignmentRequest
    val userId = unsafeUserId?.toIntOrNull() ?: throw IllegalArgumentException("userId is required")
    return AssignUserRequest(userId)
}

suspend fun ApplicationCall.receiveCreateProjectRequest(): CreateProjectRequest {
    val data = receiveParameters()
    val name = data["projectName"] ?: throw IllegalArgumentException("projectName is required")
    val dimension = data["dimension"]?.toDimension() ?: Dimension.OVERWORLD
    val priority = data["priority"]?.toPriority() ?: Priority.NONE
    val requiresPerimeter = data["requiresPerimeter"]?.toBooleanStrictOrNull() == true
    return CreateProjectRequest(name, priority, dimension, requiresPerimeter)
}

fun ApplicationCall.receiveTaskFilters() = TaskFiltersRequest(
    parameters["search"],
    parameters["sortBy"],
    parameters["assigneeFilter"],
    parameters["completionFilter"],
    parameters["taskTypeFilter"],
    parameters["amountFilter"]?.toIntOrNull()
)

fun ApplicationCall.receiveProjectFilters() = ProjectFiltersRequest(
    parameters["search"],
    parameters["hideCompleted"] == "on"
)

suspend fun ApplicationCall.receiveDoableTaskRequest(): AddDoableRequest {
    val data = receiveParameters()
    val name = data["taskName"] ?: throw IllegalArgumentException("taskName is required")
    return AddDoableRequest(name)
}

suspend fun ApplicationCall.receiveCountableTaskRequest(): AddCountableRequest {
    val data = receiveParameters()
    val name = data["taskName"] ?: throw IllegalArgumentException("taskName is required")
    val amount = data["amount"]?.toIntOrNull() ?: throw IllegalArgumentException("amount is required")
    return AddCountableRequest(name, amount)
}

suspend fun ApplicationCall.getEditCountableTaskRequirements(): EditCountableRequest {
    val data = receiveParameters()
    val id = data["id"]?.toIntOrNull() ?: throw IllegalArgumentException("id is required")
    val done = data["done"]?.toIntOrNull() ?: throw IllegalArgumentException("done is required")
    val needed = data["needed"]?.toIntOrNull() ?: throw IllegalArgumentException("needed is required")

    if (done > needed) throw IllegalArgumentException("You cannot do more than you need")

    return EditCountableRequest(id = id, needed = needed, done = done)
}

suspend fun ApplicationCall.receiveMaterialListTasks(): List<PremadeTask> {
    val data = receiveMultipart()
    val file = data.readAllParts().find { it.name == "file" } as PartData.FileItem?
    return file?.streamProvider?.let {
        it().tasksFromMaterialList()
    } ?: emptyList()
}

suspend fun ApplicationCall.receiveContraption(): ContraptionRequest {
    val data = receiveParameters()
    return ContraptionRequest(
        data["name"] ?: throw IllegalArgumentException("name is required"),
        data["description"],
        data["authors"]?.split(";") ?: throw IllegalArgumentException("authors is required"),
        data["game-type"]?.toGameType() ?: GameType.JAVA,
        data["version"]?.toContraptionVersion() ?: throw IllegalArgumentException("version is required"),
        data["schematicUrl"],
        data["worldDownloadUrl"]
    )
}

fun ApplicationCall.receiveContraptionFilterCategory(subCategoryType: SubCategoryType?): CategoryType? {
    return when(parameters["filterCategory"]) {
        "FARM" -> CategoryType.FARM
        "STORAGE" -> CategoryType.STORAGE
        "CART_TECH" -> CategoryType.CART_TECH
        "TNT_TECH" -> CategoryType.TNT_TECH
        "SLIMESTONE" -> CategoryType.SLIMESTONE
        "OTHER" -> CategoryType.OTHER
        else -> subCategoryType?.categoryType
    }
}

fun ApplicationCall.receiveContraptionFilterSubCategory(): SubCategoryType? {
    val textContent = parameters["filterSubCategory"] ?: return null
    return when {
        FarmSubcategoryType.values().map { it.name }.contains(textContent) -> FarmSubcategoryType.valueOf(textContent)
        CartTechSubCategoryType.values().map { it.name }.contains(textContent) -> CartTechSubCategoryType.valueOf(textContent)
        OtherSubCategoryType.values().map { it.name }.contains(textContent) -> OtherSubCategoryType.valueOf(textContent)
        SlimestoneSubCategoryType.values().map { it.name }.contains(textContent) -> SlimestoneSubCategoryType.valueOf(textContent)
        StorageSubCategoryType.values().map { it.name }.contains(textContent) -> StorageSubCategoryType.valueOf(textContent)
        TntTechSubCategoryType.values().map { it.name }.contains(textContent) -> TntTechSubCategoryType.valueOf(textContent)
        else -> null
    }
}

private fun String?.toDimension(): Dimension = when(this) {
    "OVERWORLD" -> Dimension.OVERWORLD
    "NETHER" -> Dimension.NETHER
    "THE_END" -> Dimension.THE_END
    else -> Dimension.OVERWORLD
}

private fun String?.toPriority(): Priority = when(this) {
    "LOW" -> Priority.LOW
    "MEDIUM" -> Priority.MEDIUM
    "HIGH" -> Priority.HIGH
    else -> Priority.NONE
}

private fun String?.toGameType(): GameType = when(this) {
    "BEDROCK" -> GameType.BEDROCK
    else -> GameType.JAVA
}

private fun String.toContraptionVersion(): ContraptionVersion {
    return if (this.contains("-")) {
        ContraptionVersion(
            lowerBound = this.split("-")[0].toWorldVersion(),
            upperBound = this.split("-")[1].toWorldVersion()
        )
    } else {
        ContraptionVersion(
            lowerBound = this.toWorldVersion(),
            upperBound = null
        )
    }
}

private fun String?.toWorldVersion(): WorldVersion {
    if (this == null) return WorldVersion(21, 0)
    if (!this.contains("""1\.[0-9]+\.[0-9]+""".toRegex())) throw IllegalArgumentException("Version not correctly formatted")
    val split = this.split(".")
    return WorldVersion(split[1].toInt(), split[2].toInt())
}