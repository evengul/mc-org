package app.mcorg.project.presentation.rest.project;

import app.mcorg.project.presentation.rest.entities.GenericResponse;
import app.mcorg.project.presentation.rest.entities.project.CreateProjectRequest;
import app.mcorg.project.presentation.rest.entities.project.ProjectListResponse;
import app.mcorg.project.presentation.rest.entities.project.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/project")
public interface ProjectResource extends ProjectDocumentation {
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ProjectListResponse>> get();

    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> delete(@PathVariable String id);

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> deleteAll(@RequestParam("confirm") Boolean confirm);

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ProjectResponse>> getProject(@PathVariable String id);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<GenericResponse>> createProject(@RequestBody @Valid CreateProjectRequest request);

    @PostMapping(value = "/schematic/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<GenericResponse>> createFromMaterialList(
            @RequestParam(value = "projectName", required = false) String name,
            @RequestPart("file") MultipartFile file
    ) throws IOException;

    @PatchMapping(value = "/{id}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> archiveProject(@PathVariable String id);

    @PatchMapping(value = "/{id}/unarchive", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<GenericResponse>> unarchiveProject(@PathVariable String id);

    @GetMapping(value = "/{id}/archived", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Boolean>> isArchived(@PathVariable String id);
}
