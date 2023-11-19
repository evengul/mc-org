package app.mcorg.team.domain.api;

import app.mcorg.common.domain.model.Authority;

public interface PermissionService {
    boolean hasAuthority(String id, Authority authority);

    boolean hasWorldAuthority(String id, Authority authority);

}
