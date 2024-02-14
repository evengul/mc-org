package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.infrastructure.entities.WorldEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoWorldRepository extends MongoRepository<WorldEntity, String> {
    List<WorldEntity> findAllByUsers_Username(String username);
}
