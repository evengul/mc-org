package app.mcorg.world.infrastructure.repository;

import app.mcorg.world.infrastructure.repository.entities.WorldEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoWorldRepository extends MongoRepository<WorldEntity, String> {
    List<WorldEntity> findAllByUsers_UsernameEqualsIgnoreCase(String username);
}
