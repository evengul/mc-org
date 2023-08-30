package app.mcorg.permission.presentation.rest.entities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "Permission")
public record PermissionResponse(@NotNull String id,
                                 @NotNull AuthorityLevelEntity level,
                                 @NotNull AuthorityEntity authority) {
}
