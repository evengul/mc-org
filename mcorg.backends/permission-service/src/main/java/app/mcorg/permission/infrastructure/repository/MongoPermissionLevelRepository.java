package app.mcorg.permission.infrastructure.repository;

import app.mcorg.permission.infrastructure.repository.entities.PermissionLevelEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoPermissionLevelRepository extends MongoRepository<PermissionLevelEntity, String> {
}
