package app.mcorg.world.domain.api;

import app.mcorg.common.domain.model.Authority;

public interface PermissionService {
    boolean hasAuthority(String id, Authority authority);
}
