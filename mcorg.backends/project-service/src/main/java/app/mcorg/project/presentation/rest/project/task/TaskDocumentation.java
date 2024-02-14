package app.mcorg.project.presentation.rest.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.presentation.rest.entities.GenericResponse;
import app.mcorg.project.presentation.rest.entities.project.SimpleProjectResponse;
import app.mcorg.project.presentation.rest.entities.project.task.AddCountedTaskRequest;
import app.mcorg.project.presentation.rest.entities.project.task.AddTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Tag(name = "Tasks")
@SecurityRequirement(name = "bearerAuth")
@SuppressWarnings("unused")
public interface TaskDocumentation {

    @Operation(summary = "Remove a task from a project with its ID")
    CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, UUID taskId);

    @Operation(summary = "Rename a task in a project")
    CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, UUID taskId, String name);

    @Operation(summary = "Reprioritize a task in a project")
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, UUID taskId, Priority priority);

    @Operation(summary = "Convert doable task to a project")
    CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(String projectId, UUID taskId);

    @Operation(summary = "Add a doable task to a project")
    CompletableFuture<ResponseEntity<GenericResponse>> addDoable(String projectId, AddTaskRequest request);

    @Operation(summary = "Mark a doable task as complete")
    CompletableFuture<ResponseEntity<GenericResponse>> complete(String projectId, UUID taskId);

    @Operation(summary = "Mark a doable task as incomplete")
    CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(String projectId, UUID taskId);

    @Operation(summary = "Add a counted task to a project")
    CompletableFuture<ResponseEntity<GenericResponse>> addCountable(String projectId, AddCountedTaskRequest request);

    @Operation(summary = "Set the needed work for a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> needsMore(String projectId, UUID taskId, int needed);

    @Operation(summary = "Set the work done for a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> doneMore(String projectId, UUID taskId, int done);

    @Operation(summary = "Add dependency to project")
    CompletableFuture<ResponseEntity<GenericResponse>> dependsOn(String projectIdOfTask, UUID taskId,
                                                                 String dependencyProjectId, Priority priority);
}
