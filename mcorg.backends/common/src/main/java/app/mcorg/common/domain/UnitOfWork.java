package app.mcorg.common.domain;

public interface UnitOfWork<T extends AggregateRoot<?>> {
    T add(T aggregateRoot);
}
