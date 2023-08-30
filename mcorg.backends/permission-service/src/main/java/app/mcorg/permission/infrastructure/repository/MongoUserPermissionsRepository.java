package app.mcorg.permission.infrastructure.repository;

import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.infrastructure.entities.UserPermissionsEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoUserPermissionsRepository extends MongoRepository<UserPermissionsEntity, String> {
    List<UserPermissionsEntity> findAllByPermissions_LevelAndPermissions_Id(AuthorityLevel level, String id);
}
