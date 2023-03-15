package app.mcorg.organizer.presentation.rest.project.countedtask;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddCountedTaskRequest;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/project/{projectId}/counted-task")
public interface CountedTaskResource extends CountedTaskDocumentation {
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> add(@PathVariable String projectId, @RequestBody @Valid AddCountedTaskRequest request);

    @DeleteMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> remove(@PathVariable String projectId, @PathVariable String taskId);

    @PatchMapping(value = "/{taskId}/rename", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    CompletableFuture<ResponseEntity<GenericResponse>> rename(@PathVariable String projectId, @PathVariable String taskId, @RequestBody @NotEmpty(message = "Task name cannot be empty") String name);

    @PatchMapping(value = "/{taskId}/reprioritize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(@PathVariable String projectId, @PathVariable String taskId, @RequestBody @Nullable Priority priority);

    @PatchMapping(value = "/{taskId}/needs-more", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> needsMore(@PathVariable String projectId, @PathVariable String taskId, @RequestBody @Positive(message = "A task must need a positive amount of work done") int needed);

    @PatchMapping(value = "/{taskId}/register-work", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> doneMore(@PathVariable String projectId, @PathVariable String taskId, @RequestBody @PositiveOrZero(message = "A task must have a positive or zero amount of work done") int done);
}
