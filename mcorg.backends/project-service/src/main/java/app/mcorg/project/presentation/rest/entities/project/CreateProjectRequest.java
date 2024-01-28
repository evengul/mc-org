package app.mcorg.project.presentation.rest.entities.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

public record CreateProjectRequest(
        @NotEmpty(message = "Project needs to be in a world")
        String worldId,
        @NotEmpty(message = "Project needs to be in a team")
        String teamId,
        @NotEmpty(message = "Project name cannot be empty")
        @Schema(example = "Project Name")
        String name) {
}
