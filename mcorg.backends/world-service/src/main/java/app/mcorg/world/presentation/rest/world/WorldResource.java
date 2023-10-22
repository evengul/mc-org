package app.mcorg.world.presentation.rest.world;

import app.mcorg.world.presentation.rest.entities.world.WorldNameChangeRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldRequest;
import app.mcorg.world.presentation.rest.entities.world.WorldResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/world")
public interface WorldResource extends WorldDocumentation {
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> createWorld(@RequestBody @Valid WorldRequest request);

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> deleteWorld(@PathVariable String id);

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> changeWorldName(@PathVariable String id, @RequestBody @Valid WorldNameChangeRequest request);

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<WorldResponse>> getWorld(@PathVariable String id);
}
