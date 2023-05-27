package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.infrastructure.entities.ProjectEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MongoProjectRepository extends MongoRepository<ProjectEntity, String> { }
