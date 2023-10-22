package app.mcorg.world.presentation.rest.entities.world;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record WorldRequest(@NotNull @NotEmpty String name) {
}
