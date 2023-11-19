package app.mcorg.team.presentation.rest.team;

import app.mcorg.team.presentation.rest.entities.team.TeamCreatedResponse;
import app.mcorg.team.presentation.rest.entities.team.TeamNameChangeRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamRequest;
import app.mcorg.team.presentation.rest.entities.team.TeamResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

@Tag(name = "Teams")
@SuppressWarnings("unused")
public interface TeamDocumentation {
    @Operation(summary = "Create a new team in a world")
    CompletableFuture<ResponseEntity<TeamCreatedResponse>> createTeam(String worldId, TeamRequest request);

    @Operation(summary = "Delete a team, forever")
    CompletableFuture<ResponseEntity<Void>> deleteTeam(String worldId, String id);

    @Operation(summary = "Change the name of a team")
    CompletableFuture<ResponseEntity<Void>> changeTeamName(String worldId, String id, TeamNameChangeRequest request);

    @Operation(summary = "Retrieve an existing team")
    CompletableFuture<ResponseEntity<TeamResponse>> getTeam(String worldId, String id);
}
