package app.mcorg.common.event.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;

public record AuthorityAdded(String authorizedId,
                             String username,
                             String name,
                             Authority authority,
                             AuthorityLevel level) implements PermissionEvent {
}
