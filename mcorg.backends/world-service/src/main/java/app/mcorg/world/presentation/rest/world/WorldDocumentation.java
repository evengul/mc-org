package app.mcorg.world.presentation.rest.world;

import app.mcorg.world.presentation.rest.entities.world.WorldNameChangeRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Worlds")
@SuppressWarnings("unused")
public interface WorldDocumentation {
    @Operation(summary = "Create a new world")
    CompletableFuture<ResponseEntity<Void>> createWorld(WorldRequest request);

    @Operation(summary = "Delete a world forever")
    CompletableFuture<ResponseEntity<Void>> deleteWorld(String id);

    @Operation(summary = "Change the name of a world")
    CompletableFuture<ResponseEntity<Void>> changeWorldName(String id, WorldNameChangeRequest request);

    @Operation(summary = "Retrieve an existing world")
    CompletableFuture<ResponseEntity<WorldResponse>> getWorld(String id);
}
