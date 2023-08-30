package app.mcorg.permission.presentation.rest.entities;

import jakarta.validation.constraints.NotNull;

public record RemovePermissionRequest(@NotNull String id,
                                      @NotNull AuthorityLevelEntity level) {
}
