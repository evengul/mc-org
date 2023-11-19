package app.mcorg.team.presentation.rest.entities.team;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreatedProject")
public record TeamCreatedResponse(@NotNull String worldId, @NotNull String id) {
}
