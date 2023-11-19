package app.mcorg.team.presentation.rest.entities.team;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record TeamNameChangeRequest(@NotNull @NotEmpty String name) {
}
