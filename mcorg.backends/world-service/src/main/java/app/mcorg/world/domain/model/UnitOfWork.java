package app.mcorg.world.domain.model;

public interface UnitOfWork<T extends AggregateRoot<?>> {
    T add(T aggregateRoot);

    void remove(String id);
}
