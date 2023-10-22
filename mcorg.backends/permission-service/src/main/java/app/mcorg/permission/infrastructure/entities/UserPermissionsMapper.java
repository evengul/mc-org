package app.mcorg.permission.infrastructure.entities;

import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.domain.model.permission.Permission;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
public class UserPermissionsMapper {
    public static UserPermissionsEntity toEntity(UserPermissions permissions) {
        return new UserPermissionsEntity(
                permissions.getId(),
                permissions.getUsername(),
                permissions.getName(),
                permissions.getPermissions()
                        .entrySet()
                        .stream()
                        .flatMap(entry -> entry.getValue().stream()
                                .map(value -> new PermissionEntity(entry.getKey(), value.id(),
                                        value.authority())))
                        .collect(Collectors.toList())
        );
    }

    public static UserPermissions toDomain(UserPermissionsEntity permissions) {
        Map<AuthorityLevel, List<Permission>> permissionsMap = new HashMap<>();
        for (AuthorityLevel value : AuthorityLevel.values()) {
            permissionsMap.put(value, new ArrayList<>());
        }
        permissions.getPermissions()
                .forEach(permission -> permissionsMap.get(permission.level())
                        .add(new Permission(permission.id(), permission.authority())));
        return new UserPermissions(
                permissions.getId(),
                permissions.getUsername(),
                permissions.getName(),
                permissionsMap
        );
    }
}
