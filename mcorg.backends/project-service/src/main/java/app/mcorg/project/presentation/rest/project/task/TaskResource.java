package app.mcorg.project.presentation.rest.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.presentation.rest.entities.GenericResponse;
import app.mcorg.project.presentation.rest.entities.project.SimpleProjectResponse;
import app.mcorg.project.presentation.rest.entities.project.task.AddCountedTaskRequest;
import app.mcorg.project.presentation.rest.entities.project.task.AddTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/project/{projectId}/task")
public interface TaskResource extends TaskDocumentation {

    @DeleteMapping(value = "/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> remove(
            @PathVariable String projectId,
            @PathVariable UUID taskId);

    @PatchMapping(value = "/{taskId}/rename", produces = MediaType.APPLICATION_JSON_VALUE,
                  consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> rename(
            @PathVariable String projectId,
            @PathVariable UUID taskId,
            @RequestParam
            @NotEmpty(message = "Task name cannot be empty") String name);

    @PatchMapping(value = "/{taskId}/reprioritize", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(
            @PathVariable String projectId,
            @PathVariable UUID taskId,
            @RequestParam Priority priority);

    @PatchMapping(value = "/doable/{taskId}/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(
            @PathVariable String projectId,
            @PathVariable UUID taskId);

    @PostMapping(value = "/doable", produces = MediaType.APPLICATION_JSON_VALUE,
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<GenericResponse>> addDoable(
            @PathVariable String projectId,
            @RequestBody
            @Valid AddTaskRequest request);

    @PatchMapping(value = "/doable/{taskId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> complete(
            @PathVariable String projectId,
            @PathVariable UUID taskId);

    @PatchMapping(value = "/doable/{taskId}/incomplete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(
            @PathVariable String projectId,
            @PathVariable UUID taskId);

    @PostMapping(value = "/countable", consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> addCountable(
            @PathVariable String projectId,
            @RequestBody
            @Valid AddCountedTaskRequest request);

    @PatchMapping(value = "/countable/{taskId}/needs-more", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> needsMore(
            @PathVariable String projectId,
            @PathVariable UUID taskId,
            @RequestParam
            @Positive(message = "A task must need a positive amount of work done") int needed);

    @PatchMapping(value = "/countable/{taskId}/register-work", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> doneMore(
            @PathVariable String projectId,
            @PathVariable UUID taskId,
            @RequestParam
            @PositiveOrZero(message = "A task must have a positive or zero amount of work done") int done);

    @PatchMapping(value = "/{taskId}/depends-on-project", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> dependsOn(
            @PathVariable("projectId") String projectIdOfTask,
            @PathVariable("taskId") UUID taskId,
            @RequestParam
            @NotEmpty String dependencyProjectId,
            @RequestParam(required = false, defaultValue = "LOW") Priority priority);
}
