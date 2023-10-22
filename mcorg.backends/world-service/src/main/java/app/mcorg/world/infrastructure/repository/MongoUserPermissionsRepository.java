package app.mcorg.world.infrastructure.repository;

import app.mcorg.world.infrastructure.repository.entities.UserPermissionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MongoUserPermissionsRepository extends MongoRepository<UserPermissionEntity, String> {
    Optional<UserPermissionEntity> findByUsernameIgnoreCase(String username);

    void deleteByUsernameIgnoreCase(String username);
}
