package app.mcorg.permission.infrastructure.repository.entities;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;

public record PermissionEntity(AuthorityLevel level,
                               String id,
                               Authority authority) {
}
