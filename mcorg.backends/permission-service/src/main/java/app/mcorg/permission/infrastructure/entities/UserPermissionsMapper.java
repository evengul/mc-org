package app.mcorg.permission.infrastructure.entities;

import app.mcorg.permission.domain.model.permission.Permission;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import lombok.experimental.UtilityClass;

import java.util.stream.Collectors;

@UtilityClass
public class UserPermissionsMapper {
    public static UserPermissionsEntity toEntity(UserPermissions permissions) {
        return new UserPermissionsEntity(
                permissions.getId(),
                permissions.getUsername(),
                permissions.getPermissions()
                           .entrySet()
                           .stream()
                           .flatMap(entry -> entry.getValue().stream()
                                                  .map(value -> new PermissionEntity(entry.getKey(), value.id(),
                                                                                     value.authority())))
                           .toList()
        );
    }

    public static UserPermissions toDomain(UserPermissionsEntity permissions) {
        return new UserPermissions(
                permissions.getId(),
                permissions.getUsername(),
                permissions.getPermissions()
                           .stream()
                           .collect(Collectors.groupingBy(PermissionEntity::level, Collectors.mapping(
                                   entity -> new Permission(entity.id(), entity.authority()),
                                   Collectors.toList())))
        );
    }
}
