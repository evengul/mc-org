package app.mcorg.resources.presentation.entities;

import app.mcorg.resources.domain.model.resource.Resource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ResourceRequest(@NotNull @NotEmpty @Schema(example = "ResourceName") String name,
                              @NotNull @NotEmpty @Schema(example = "MOD") Resource.Type type,
                              @NotNull @NotEmpty @Schema(example = "https://example.com") String url) {
}
