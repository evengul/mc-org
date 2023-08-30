package app.mcorg.permission.presentation.rest.entities;

import app.mcorg.permission.domain.model.permission.UserPermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "UserPermissions")
public record UserPermissionsResponse(@NotNull String username,
                                      @NotNull List<PermissionResponse> permissions) {
    public static UserPermissionsResponse from(UserPermissions permissions) {
        return new UserPermissionsResponse(
                permissions.getUsername(),
                permissions.getPermissions().entrySet()
                           .stream()
                           .flatMap(entry -> entry.getValue()
                                                  .stream()
                                                  .map(value -> new PermissionResponse(value.id(),
                                                                                       AuthorityLevelEntity.from(
                                                                                               entry.getKey()),
                                                                                       AuthorityEntity.from(
                                                                                               value.authority()))))
                           .toList()
        );
    }
}
