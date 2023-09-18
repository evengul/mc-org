package app.mcorg.world.infrastructure.repository.mappers;

import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.infrastructure.repository.entities.WorldEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class WorldMapper {
    public static WorldEntity toEntity(World world) {
        return new WorldEntity(world.getId(), world.getName(), world.getUsers(), world.getTeams());
    }

    public static World toDomain(WorldEntity entity) {
        return new World(entity.getId(), entity.getName(), entity.getUsers(), entity.getTeams());
    }
}
