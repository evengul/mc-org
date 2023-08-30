package app.mcorg.common.domain;

import app.mcorg.common.event.DomainEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class AggregateRoot<T extends DomainEvent> {
    protected List<T> domainEvents = new ArrayList<>();

    public void raiseEvent(T event) {
        domainEvents.add(event);
    }
}
