package app.mcorg.resources.presentation.entities;

import app.mcorg.resources.domain.model.resource.ServerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ResourcePackRequest(@NotNull @NotEmpty @Schema(example = "TestPack") String name,
                                  @NotNull @NotEmpty @Schema(example = "1.19.4") String version,
                                  @NotNull @NotEmpty @Schema(example = "FABRIC") ServerType type) {
}
