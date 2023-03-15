package app.mcorg.organizer.presentation.rest.project.task;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddTaskRequest;
import app.mcorg.organizer.presentation.rest.entities.project.SimpleProjectResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/project/{projectId}/task")
public interface TaskResource extends TaskDocumentation {
    @PatchMapping(value = "/{taskId}/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(String projectId, String taskId);

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<GenericResponse>> add(@PathVariable String projectId, @RequestBody @Valid AddTaskRequest request);

    @DeleteMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> remove(@PathVariable String projectId, @PathVariable String taskId);

    @PatchMapping(value = "/{taskId}/rename", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> rename(@PathVariable String projectId, @PathVariable String taskId, @RequestBody @NotEmpty(message = "Task name cannot be empty") String name);

    @PatchMapping(value = "/{taskId}/reprioritize", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(@PathVariable String projectId, @PathVariable String taskId, @RequestBody Priority priority);

    @PatchMapping(value = "/{taskId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> complete(@PathVariable String projectId, @PathVariable String taskId);

    @PatchMapping(value = "/{taskId}/incomplete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(@PathVariable String projectId, @PathVariable String taskId);
}
