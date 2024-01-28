package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.permission.UserPermissions;

import java.util.Optional;

public interface Permissions {
    Optional<UserPermissions> get(String username);

    void store(UserPermissions permissions);

    void delete(String username);
}
