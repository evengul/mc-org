package app.mcorg.team.infrastructure.repository.mappers;

import app.mcorg.team.domain.model.permission.UserPermissions;
import app.mcorg.team.infrastructure.repository.entities.UserPermissionEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserPermissionsMapper {
    public static UserPermissionEntity toEntity(UserPermissions permissions) {
        return new UserPermissionEntity(permissions.id(), permissions.username(), permissions.teamPermissions());
    }

    public static UserPermissions toDomain(UserPermissionEntity entity) {
        return new UserPermissions(entity.getId(), entity.getUsername(), entity.getWorldAuthorities());
    }
}
