package app.mcorg.permission.presentation.rest.entities;

import jakarta.validation.constraints.NotNull;

public record AddPermissionRequest(@NotNull String id,
                                   @NotNull AuthorityLevelEntity level,
                                   @NotNull AuthorityEntity authority) {
}
