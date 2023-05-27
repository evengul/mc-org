package app.mcorg.resources.infrastructure.repository;

import app.mcorg.resources.infrastructure.repository.entities.ResourcePackEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoResourceRepository extends MongoRepository<ResourcePackEntity, String> {
    List<ResourcePackEntity> findAllByVersionEqualsIgnoreCase(String version);
}
