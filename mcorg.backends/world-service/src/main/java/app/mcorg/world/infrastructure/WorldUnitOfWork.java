package app.mcorg.world.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.common.event.world.WorldEvent;
import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.infrastructure.repository.MongoWorldRepository;
import app.mcorg.world.infrastructure.repository.mappers.WorldMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorldUnitOfWork implements UnitOfWork<World> {
    private final EventDispatcher<WorldEvent> dispatcher;
    private final MongoWorldRepository repository;

    @Override
    public World add(World aggregateRoot) {
        World stored = WorldMapper.toDomain(
                repository.save(WorldMapper.toEntity(aggregateRoot))
        );
        dispatcher.dispatch(aggregateRoot.getDomainEvents());
        return stored;
    }

    @Override
    public void remove(String id) {
        repository.findById(id)
                .ifPresent(world -> {
                    repository.deleteById(world.getId());
                    dispatcher.dispatch(new WorldDeleted(world.getId()));
                });
    }
}
