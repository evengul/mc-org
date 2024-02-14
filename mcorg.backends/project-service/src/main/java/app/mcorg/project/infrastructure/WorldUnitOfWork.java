package app.mcorg.project.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.common.event.world.WorldEvent;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.infrastructure.entities.mappers.WorldMapper;
import app.mcorg.project.infrastructure.repository.MongoWorldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorldUnitOfWork implements UnitOfWork<World> {
    private final MongoWorldRepository repository;
    private final EventDispatcher<WorldEvent> dispatcher;

    @Override
    public World add(World aggregateRoot) {
        World stored = WorldMapper.toDomain(repository.save(WorldMapper.toEntity(aggregateRoot)));
        dispatcher.dispatch(aggregateRoot.getDomainEvents());
        return stored;
    }

    @Override
    public void remove(String id) {
        dispatcher.dispatch(new WorldDeleted(id));
    }
}
