package app.mcorg.project.presentation.rest.entities.project.task;

import app.mcorg.common.domain.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record AddCountedTaskRequest(@NotEmpty(message = "Task name cannot be empty")
                                    @Schema(example = "Dirt")
                                    String name,
                                    @Nullable
                                    @Schema(example = "LOW")
                                    Priority priority,
                                    @Positive(message = "A countable task must need a positive amount")
                                    @Schema(example = "64")
                                    Integer needed) {
}
