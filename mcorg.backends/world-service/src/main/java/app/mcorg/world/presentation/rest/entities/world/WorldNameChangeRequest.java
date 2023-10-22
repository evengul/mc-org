package app.mcorg.world.presentation.rest.entities.world;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record WorldNameChangeRequest(@NotNull @NotEmpty String name) {
}
