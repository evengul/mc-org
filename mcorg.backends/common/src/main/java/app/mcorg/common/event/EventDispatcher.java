package app.mcorg.common.event;

import java.util.List;

public interface EventDispatcher<T extends DomainEvent> {
    void dispatch(T event);

    default void dispatch(List<T> events) {
        events.forEach(this::dispatch);
    }
}
