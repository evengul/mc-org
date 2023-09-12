package app.mcorg.world.domain.api;

import app.mcorg.world.domain.event.DomainEvent;

import java.util.List;

public interface EventDispatcher<T extends DomainEvent> {
    void dispatch(T event);

    void dispatch(List<T> events);
}
