package app.mcorg.permission.domain.model.permission;

import app.mcorg.common.domain.model.Authority;

public record Permission(String id,
                         Authority authority) {
}
