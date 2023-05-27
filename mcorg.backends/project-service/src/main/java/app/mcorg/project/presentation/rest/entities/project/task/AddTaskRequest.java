package app.mcorg.project.presentation.rest.entities.project.task;

import app.mcorg.project.domain.model.project.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

public record AddTaskRequest(@NotEmpty(message = "Task name cannot be empty")
                             @Schema(example = "Build a palace") String name,
                             @Nullable
                             @Schema(example = "HIGH")
                             Priority priority) {
}
