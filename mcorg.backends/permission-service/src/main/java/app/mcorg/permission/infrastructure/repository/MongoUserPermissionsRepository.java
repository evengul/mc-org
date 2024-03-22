package app.mcorg.permission.infrastructure.repository;

import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.infrastructure.repository.entities.UserPermissionsEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MongoUserPermissionsRepository extends MongoRepository<UserPermissionsEntity, String> {
    Optional<UserPermissionsEntity> findFirstByUsernameIgnoreCase(String username);

    List<UserPermissionsEntity> findAllByPermissions_LevelAndPermissions_Id(AuthorityLevel level, String id);
}
