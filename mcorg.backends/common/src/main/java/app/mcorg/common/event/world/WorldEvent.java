package app.mcorg.common.event.world;

import app.mcorg.common.event.DomainEvent;

public sealed interface WorldEvent extends DomainEvent permits
                                                       WorldCreated,
                                                       WorldDeleted,
                                                       WorldNameChanged {
    String id();
}
