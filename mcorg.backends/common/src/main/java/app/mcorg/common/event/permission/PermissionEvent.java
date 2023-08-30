package app.mcorg.common.event.permission;

import app.mcorg.common.event.DomainEvent;

public sealed interface PermissionEvent extends DomainEvent
        permits
        UserDeleted,
        AuthorityAdded,
        AuthorityChanged,
        AuthorityRemoved {
    String username();
}
