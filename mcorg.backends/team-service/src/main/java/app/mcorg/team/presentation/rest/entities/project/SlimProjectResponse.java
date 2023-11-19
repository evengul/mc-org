package app.mcorg.team.presentation.rest.entities.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "TeamProject")
public record SlimProjectResponse(@NotNull String id, @NotNull String name) {
}
