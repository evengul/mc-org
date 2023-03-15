package app.mcorg.organizer.presentation.rest.project.countedtask;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddCountedTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Counted Tasks")
public interface CountedTaskDocumentation {
    @Operation(summary = "Add a counted task to a project")
    CompletableFuture<ResponseEntity<GenericResponse>> add(String projectId, AddCountedTaskRequest request);
    @Operation(summary = "Remove a counted task from a project")
    CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, String taskId);
    @Operation(summary = "Rename a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, String taskId, String name);
    @Operation(summary = "Reprioritize a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, String taskId, Priority priority);
    @Operation(summary = "Set the needed work for a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> needsMore(String projectId, String taskId, int needed);
    @Operation(summary = "Set the work done for a counted task")
    CompletableFuture<ResponseEntity<GenericResponse>> doneMore(String projectId, String taskId, int done);
}
