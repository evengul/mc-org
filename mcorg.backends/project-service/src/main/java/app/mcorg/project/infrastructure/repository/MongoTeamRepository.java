package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.infrastructure.entities.TeamEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoTeamRepository extends MongoRepository<TeamEntity, String> {
    List<TeamEntity> findAllByUsers_Username(String username);
}
