package app.mcorg.resources.presentation.resource;

import app.mcorg.resources.presentation.entities.ResourcePackRequest;
import app.mcorg.resources.presentation.entities.ResourcePackResponse;
import app.mcorg.resources.presentation.entities.ResourcePacksResponse;
import app.mcorg.resources.presentation.entities.ResourceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Tag(name = "Resources")
public interface ResourceDocumentation {
    @Operation(summary = "Get a single resource pack")
    CompletableFuture<ResponseEntity<ResourcePackResponse>> getResourcepack(String id);

    @Operation(summary = "Retrieves all resourcePacks for a version")
    CompletableFuture<ResponseEntity<ResourcePacksResponse>> getResourcesForVersion(String version);

    @Operation(summary = "Retrieves all available resourcePacks for all versions")
    CompletableFuture<ResponseEntity<ResourcePacksResponse>> getAll();

    @Operation(summary = "Create a new resourcePack")
    CompletableFuture<ResponseEntity<Void>> create(HttpServletRequest httpServletRequest, ResourcePackRequest request);

    @Operation(summary = "Add a versioned URL to a resourcePack")
    CompletableFuture<ResponseEntity<Void>> addVersionedUrl(String packId, ResourceRequest request);
}
