package app.mcorg.common.event.permission;

import app.mcorg.common.domain.model.AuthorityLevel;

public record AuthorityRemoved(String authorizedId,
                               String username,
                               String name,
                               AuthorityLevel level) implements PermissionEvent {
}
