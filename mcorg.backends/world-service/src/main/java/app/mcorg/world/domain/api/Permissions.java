package app.mcorg.world.domain.api;

import app.mcorg.world.domain.model.permission.UserPermissions;

import java.util.Optional;

public interface Permissions {
    Optional<UserPermissions> get(String username);

    void store(UserPermissions permissions);

    void delete(String username);
}
