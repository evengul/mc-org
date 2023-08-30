package app.mcorg.permission.presentation.rest.entities;

import jakarta.validation.constraints.NotNull;

public record ChangeAuthorityRequest(@NotNull String id,
                                     @NotNull AuthorityLevelEntity level,
                                     @NotNull AuthorityEntity authority) {
}
