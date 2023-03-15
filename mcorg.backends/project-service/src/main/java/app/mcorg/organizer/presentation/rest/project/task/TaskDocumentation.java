package app.mcorg.organizer.presentation.rest.project.task;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddTaskRequest;
import app.mcorg.organizer.presentation.rest.entities.project.SimpleProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Doable tasks")
public interface TaskDocumentation {
    @Operation(summary = "Convert to a project")
    CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(String projectId, String taskId);
    @Operation(summary = "Add a task to a project")
    CompletableFuture<ResponseEntity<GenericResponse>> add(String projectId, AddTaskRequest request);
    @Operation(summary = "Remove a task from a project with its ID")
    CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, String taskId);
    @Operation(summary = "Rename a task in a project")
    CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, String taskId, String name);
    @Operation(summary = "Reprioritize a task in a project")
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, String taskId, Priority priority);
    @Operation(summary = "Mark a task as complete")
    CompletableFuture<ResponseEntity<GenericResponse>> complete(String projectId, String taskId);
    @Operation(summary = "Mark a task as incomplete")
    CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(String projectId, String taskId);
}
