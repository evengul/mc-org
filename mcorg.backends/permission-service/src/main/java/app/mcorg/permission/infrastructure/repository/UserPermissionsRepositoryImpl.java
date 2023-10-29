package app.mcorg.permission.infrastructure.repository;

import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.infrastructure.entities.UserPermissionsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserPermissionsRepositoryImpl implements Permissions {
    private final MongoUserPermissionsRepository repository;

    @Override
    public Optional<UserPermissions> get(String username) {
        return repository.findFirstByUsernameIgnoreCase(username)
                .map(UserPermissionsMapper::toDomain);
    }
}
