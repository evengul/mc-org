package app.mcorg.world.infrastructure.repository.mappers;

import app.mcorg.world.domain.model.permission.UserPermissions;
import app.mcorg.world.infrastructure.repository.entities.UserPermissionEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class UserPermissionsMapper {
    public static UserPermissionEntity toEntity(UserPermissions permissions) {
        return new UserPermissionEntity(permissions.id(), permissions.username(), permissions.name(), permissions.worldAuthorities());
    }

    public static UserPermissions toDomain(UserPermissionEntity entity) {
        return new UserPermissions(entity.getId(), entity.getUsername(), entity.getName(), entity.getWorldAuthorities());
    }
}
