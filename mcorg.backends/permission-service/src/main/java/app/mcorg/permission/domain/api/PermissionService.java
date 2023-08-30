package app.mcorg.permission.domain.api;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;

public interface PermissionService {
    boolean hasAuthority(AuthorityLevel level, String id, Authority authority);

}
