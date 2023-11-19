package app.mcorg.team.infrastructure.repository;

import app.mcorg.team.infrastructure.repository.entities.TeamEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MongoTeamRepository extends MongoRepository<TeamEntity, String> {
    List<TeamEntity> findAllByUsersContainingIgnoreCase(String username);

    List<TeamEntity> findAllByWorldId(String worldId);
}
