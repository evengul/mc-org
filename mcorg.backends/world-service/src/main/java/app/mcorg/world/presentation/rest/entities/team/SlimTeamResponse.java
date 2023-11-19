package app.mcorg.world.presentation.rest.entities.team;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "WorldTeam")
public record SlimTeamResponse(@NotNull String id, @NotNull String name) {
}
