package app.mcorg.permission.domain.api;


import app.mcorg.permission.domain.model.permission.UserPermissions;

import java.util.Optional;

public interface Permissions {
    Optional<UserPermissions> get(String username);
}
