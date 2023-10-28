package app.mcorg.common.event.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;

public record AuthorityAdded(String authorizedId,
                             String username,
                             Authority authority,
                             AuthorityLevel level) implements PermissionEvent {
}
