package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.project.domain.model.permission.Permission;
import app.mcorg.project.domain.model.permission.UserPermissions;
import app.mcorg.project.infrastructure.entities.UserPermissionEntity;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class UserPermissionsMapper {
    public static UserPermissionEntity toEntity(UserPermissions permissions) {
        return new UserPermissionEntity(permissions.id(), permissions.username(), permissions.permissions().get(AuthorityLevel.WORLD), permissions.permissions().get(AuthorityLevel.TEAM));
    }

    public static UserPermissions toDomain(UserPermissionEntity entity) {
        Map<AuthorityLevel, List<Permission>> permissions = new HashMap<>();
        permissions.put(AuthorityLevel.WORLD, new ArrayList<>());
        permissions.put(AuthorityLevel.TEAM, new ArrayList<>());
        entity.getWorldPermissions().forEach(permission -> permissions.get(AuthorityLevel.WORLD).add(permission));
        entity.getTeamPermissions().forEach(permission -> permissions.get(AuthorityLevel.TEAM).add(permission));
        return new UserPermissions(entity.getId(), entity.getUsername(), permissions);
    }
}
