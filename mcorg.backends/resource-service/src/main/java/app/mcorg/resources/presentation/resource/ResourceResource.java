package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.presentation.entities.ResourcePackRequest;
import app.mcorg.resources.presentation.entities.ResourcePackResponse;
import app.mcorg.resources.presentation.entities.ResourcePacksResponse;
import app.mcorg.resources.presentation.entities.ResourceRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/resource-pack")
public interface ResourceResource extends ResourceDocumentation {

    @GetMapping("/{packId}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ResourcePackResponse>> getResourcepack(@PathVariable("packId") String id);

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ResourcePacksResponse>> getAll();

    @GetMapping(value = "/version/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<ResourcePacksResponse>> getResourcesForVersion(@PathVariable String version);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<Void>> create(HttpServletRequest httpServletRequest, @RequestBody @Valid ResourcePackRequest request);

    @PatchMapping("/{packId}/resource")
    CompletableFuture<ResponseEntity<Void>> addVersionedUrl(@PathVariable("packId") String packId, @RequestBody @Valid ResourceRequest request);
}
