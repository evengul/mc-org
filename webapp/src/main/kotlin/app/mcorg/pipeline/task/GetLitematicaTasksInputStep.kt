package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.GetLitematicaTasksInputStepFailure
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream

object GetLitematicaTasksInputStep : Step<MultiPartData, GetLitematicaTasksInputStepFailure, InputStream> {
    override suspend fun process(input: MultiPartData): Result<GetLitematicaTasksInputStepFailure, InputStream> {
        var inputStream: InputStream? = null
        input.forEachPart {
            if (it is PartData.FileItem) {
                inputStream = it.provider().toInputStream()
            }
        }

        return inputStream?.let { Result.success(it) } ?: Result.failure(GetLitematicaTasksInputStepFailure.NoFile)
    }
}