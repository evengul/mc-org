package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.presentation.entities.ResourceResponse;
import app.mcorg.resources.presentation.entities.ResourcesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Tag(name = "Resources")
public interface ResourceDocumentation {
    @Operation(summary = "Retrieves all resources for a version")
    CompletableFuture<ResponseEntity<ResourcesResponse>> getResourcesForVersion(String version);

    @Operation(summary = "Retrieves all available resources for all versions")
    CompletableFuture<ResponseEntity<List<ResourceResponse>>> getAll();
}
