package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.presentation.entities.ResourceResponse;
import app.mcorg.resources.presentation.entities.ResourcesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/resource")
public interface ResourceResource extends ResourceDocumentation {
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<List<ResourceResponse>>> getAll();

    @GetMapping(value = "/version/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ResourcesResponse>> getResourcesForVersion(@PathVariable String version);
}
