package app.mcorg.team.presentation.rest.team;

import app.mcorg.team.presentation.rest.entities.team.TeamCreatedResponse;
import app.mcorg.team.presentation.rest.entities.team.TeamNameChangeRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/world/{worldId}/team")
public interface TeamResource extends TeamDocumentation {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CompletableFuture<ResponseEntity<TeamCreatedResponse>> createTeam(@PathVariable String worldId,
                                                                      @RequestBody @Valid TeamRequest request);

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> deleteTeam(@PathVariable String worldId, @PathVariable String id);

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<Void>> changeTeamName(@PathVariable String worldId,
                                                           @PathVariable String id,
                                                           @RequestBody @Valid TeamNameChangeRequest request);

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    CompletableFuture<ResponseEntity<TeamResponse>> getTeam(@PathVariable String worldId, @PathVariable String id);
}
