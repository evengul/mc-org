package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.domain.api.Worlds;
import app.mcorg.project.domain.exceptions.NotFoundException;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.infrastructure.entities.mappers.WorldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WorldRepositoryImpl implements Worlds {
    private final MongoWorldRepository repository;

    @Override
    public List<World> getAll(String username) {
        return repository.findAllByUsers_Username(username)
                         .stream()
                         .map(WorldMapper::toDomain)
                         .toList();
    }

    @Override
    public World get(String id) {
        return repository.findById(id)
                         .map(WorldMapper::toDomain)
                         .orElseThrow(() -> new NotFoundException(id));
    }
}
