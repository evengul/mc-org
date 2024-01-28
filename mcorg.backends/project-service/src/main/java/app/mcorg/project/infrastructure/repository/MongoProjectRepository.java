package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.infrastructure.entities.ProjectEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MongoProjectRepository extends MongoRepository<ProjectEntity, String> {
    List<ProjectEntity> findAllByTeamId(String teamId);

    List<ProjectEntity> findAllByWorldId(String worldId);

    List<ProjectEntity> findAllByUsersContainingIgnoreCase(String username);
}
