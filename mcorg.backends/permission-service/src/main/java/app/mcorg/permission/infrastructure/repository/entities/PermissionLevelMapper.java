package app.mcorg.permission.infrastructure.repository.entities;

import app.mcorg.permission.domain.model.permission.PermissionLevel;
import lombok.experimental.UtilityClass;

import static java.util.Objects.isNull;

@UtilityClass
public class PermissionLevelMapper {
    public static PermissionLevelEntity toEntity(PermissionLevel level) {
        return new PermissionLevelEntity(
                level.id(),
                level.authorityLevel(),
                isNull(level.parent()) ? null : toEntity(level.parent())
        );
    }

    public static PermissionLevel toDomain(PermissionLevelEntity entity) {
        return new PermissionLevel(
                entity.getId(),
                entity.getLevel(),
                isNull(entity.getParent()) ? null : toDomain(entity.getParent())
        );
    }
}
