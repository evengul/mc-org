package app.mcorg.presentation.mappers.task

import app.mcorg.domain.projects.PremadeTask
import app.mcorg.presentation.mappers.InputMappers
import io.ktor.http.content.*
import io.ktor.utils.io.jvm.javaio.*

suspend fun InputMappers.Companion.createTasksFromMaterialListInputMapper(data: MultiPartData): List<PremadeTask> {
    var materialList = emptyList<PremadeTask>()
    data.forEachPart {
        if (it is PartData.FileItem) {
            materialList = PremadeTask.from(it.provider().toInputStream())
        }
    }
    return materialList
}