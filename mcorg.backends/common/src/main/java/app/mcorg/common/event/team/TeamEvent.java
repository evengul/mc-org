package app.mcorg.common.event.team;

import app.mcorg.common.event.DomainEvent;

public sealed interface TeamEvent extends DomainEvent permits
                                                      TeamCreated,
                                                      TeamDeleted,
                                                      TeamNameChanged {
    String id();
}
