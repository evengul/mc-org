package app.mcorg.presentation.plugins

import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.project.EnsureProjectExistsInWorldFailure
import app.mcorg.pipeline.project.EnsureProjectExistsInWorldStep
import app.mcorg.pipeline.project.EnsureUserExistsInProject
import app.mcorg.pipeline.project.EnsureUserExistsInProjectFailure
import app.mcorg.pipeline.project.GetProjectIdParamFailure
import app.mcorg.pipeline.project.GetProjectIdParamStep
import app.mcorg.pipeline.project.ProjectParamFailure
import app.mcorg.pipeline.task.EnsureTaskExistsInProject
import app.mcorg.pipeline.task.EnsureTaskExistsInProjectFailure
import app.mcorg.pipeline.task.GetTaskIdParameterFailure
import app.mcorg.pipeline.task.GetTaskIdParameterStep
import app.mcorg.pipeline.task.TaskParamFailure
import app.mcorg.pipeline.world.EnsureWorldExistsFailure
import app.mcorg.pipeline.world.EnsureWorldExistsStep
import app.mcorg.pipeline.world.GetWorldIdParameterFailure
import app.mcorg.pipeline.world.GetWorldIdParameterStep
import app.mcorg.pipeline.world.WorldParamFailure
import app.mcorg.presentation.utils.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.response.*

val WorldParamPlugin = createRouteScopedPlugin("WorldParamPlugin") {
    onCall {
        val userId = it.getUserId()
        Pipeline.create<WorldParamFailure, Parameters>()
            .pipe(GetWorldIdParameterStep)
            .pipe(object : Step<Int, WorldParamFailure, Int> {
                override suspend fun process(input: Int): Result<WorldParamFailure, Int> {
                    return EnsureUserExistsInProject(input).process(userId).map { input }.mapError { error ->
                        when (error) {
                            is EnsureUserExistsInProjectFailure.Other -> WorldParamFailure.Other(error.failure)
                            EnsureUserExistsInProjectFailure.UserNotFound -> WorldParamFailure.UserNotInWorld
                        }
                    }
                }
            })
            .pipe(EnsureWorldExistsStep)
            .fold(
                input = it.parameters,
                onSuccess = { worldId -> it.setWorldId(worldId) },
                onFailure = { error ->
                    when (error) {
                        is EnsureWorldExistsFailure.Other -> it.respond(
                            HttpStatusCode.InternalServerError,
                            "An unknown error occurred"
                        )
                        EnsureWorldExistsFailure.WorldNotFound -> it.respond(HttpStatusCode.NotFound)
                        GetWorldIdParameterFailure.WorldIdNotPresent -> it.respondRedirect(
                            "/app/worlds",
                            permanent = false
                        )
                        is WorldParamFailure.Other -> it.respond(
                            HttpStatusCode.InternalServerError,
                            "An unknown error occurred"
                        )
                        WorldParamFailure.UserNotInWorld -> it.respond(HttpStatusCode.Forbidden, "You do not have access to this world")
                    }
                }
            )
    }
}

val ProjectParamPlugin = createRouteScopedPlugin("ProjectParamPlugin") {
    onCall {
        val worldId = it.getWorldId()
        Pipeline.create<ProjectParamFailure, Parameters>()
            .pipe(GetProjectIdParamStep)
            .pipe(EnsureProjectExistsInWorldStep(worldId))
            .fold(
                input = it.parameters,
                onSuccess = { projectId -> it.setProjectId(projectId) },
                onFailure = { error -> when(error) {
                    is EnsureProjectExistsInWorldFailure.Other -> it.respond(
                        HttpStatusCode.InternalServerError,
                        "An unknown error occurred"
                    )
                    EnsureProjectExistsInWorldFailure.ProjectNotFound -> it.respond(HttpStatusCode.NotFound, "Project not found in world")
                    GetProjectIdParamFailure.ProjectIdNotPresent -> it.respondRedirect("/app/worlds/$worldId/projects")
                } }
            )
    }
}

val TaskParamPlugin = createRouteScopedPlugin("TaskParamPlugin") {
    onCall {
        val worldId = it.getWorldId()
        val projectId = it.getProjectId()
        Pipeline.create<TaskParamFailure, Parameters>()
            .pipe(GetTaskIdParameterStep)
            .pipe(EnsureTaskExistsInProject(projectId))
            .fold(
                input = it.parameters,
                onSuccess = { taskId -> it.setTaskId(taskId) },
                onFailure = { error -> when(error) {
                    is EnsureTaskExistsInProjectFailure.Other -> it.respond(
                        HttpStatusCode.InternalServerError,
                        "An unknown error occurred"
                    )
                    EnsureTaskExistsInProjectFailure.TaskNotFound -> it.respondNotFound("Task not found in project")
                    GetTaskIdParameterFailure.TaskIdNotPresent -> it.respondRedirect("/app/worlds/$worldId/projects/$projectId/tasks", permanent = false)
                }}
            )
    }
}