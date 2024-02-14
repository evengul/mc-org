package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.infrastructure.entities.WorldEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WorldMapper {
    public static WorldEntity toEntity(World world) {
        return new WorldEntity(
                world.getId(),
                world.getName(),
                world.getTeams(),
                world.getUsers()
        );
    }

    public static World toDomain(WorldEntity entity) {
        return new World(
                entity.getId(),
                entity.getName(),
                entity.getTeams(),
                entity.getUsers()
        );
    }
}
