package app.mcorg.pipeline.project

import app.mcorg.domain.model.task.TaskSpecification
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import io.ktor.http.Parameters

data object GetTaskSpecificationFromFormStep : Step<Parameters, GetProjectFailure, TaskSpecification> {
    override suspend fun process(input: Parameters): Result<GetProjectFailure, TaskSpecification> {
        return Result.success(TaskSpecification(
            input["search"],
            input["sortBy"],
            input["assigneeFilter"],
            input["completionFilter"],
            input["taskTypeFilter"],
            input["amountFilter"]?.toIntOrNull()
        ))
    }
}