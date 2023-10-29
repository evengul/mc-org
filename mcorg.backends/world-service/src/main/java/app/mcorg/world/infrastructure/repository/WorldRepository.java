package app.mcorg.world.infrastructure.repository;

import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.infrastructure.repository.mappers.WorldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorldRepository implements Worlds {

    private final MongoWorldRepository repository;

    @Override
    public Optional<World> get(String id) {
        return repository.findById(id)
                .map(WorldMapper::toDomain);
    }

    @Override
    public List<World> getWorldsWithUser(String username) {
        return repository.findAllByUsersContainingIgnoreCase(username)
                .stream()
                .map(WorldMapper::toDomain)
                .toList();
    }
}
