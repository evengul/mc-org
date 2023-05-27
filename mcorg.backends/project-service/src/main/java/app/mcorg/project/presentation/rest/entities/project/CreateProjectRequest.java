package app.mcorg.project.presentation.rest.entities.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

public record CreateProjectRequest(
        @NotEmpty(message = "Project name cannot be empty")
        @Schema(example = "Project Name")
        String name) {
}
