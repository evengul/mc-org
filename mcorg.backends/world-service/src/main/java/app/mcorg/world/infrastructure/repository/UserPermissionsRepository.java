package app.mcorg.world.infrastructure.repository;

import app.mcorg.world.domain.api.Permissions;
import app.mcorg.world.domain.model.permission.UserPermissions;
import app.mcorg.world.infrastructure.repository.mappers.UserPermissionsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserPermissionsRepository implements Permissions {

    private final MongoUserPermissionsRepository repository;

    @Override
    public Optional<UserPermissions> get(String username) {
        return repository.findByUsernameIgnoreCase(username)
                .map(UserPermissionsMapper::toDomain);
    }

    @Override
    public void store(UserPermissions permissions) {
        repository.save(UserPermissionsMapper.toEntity(permissions));
    }

    @Override
    public void delete(String username) {
        repository.deleteByUsernameIgnoreCase(username);
    }
}
