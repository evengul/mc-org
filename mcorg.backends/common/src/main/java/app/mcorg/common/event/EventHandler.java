package app.mcorg.common.event;

import java.util.List;

public interface EventHandler<T extends DomainEvent> {
    void dispatch(T event);
    void dispatch(List<T> events);
}
