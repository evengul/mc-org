package app.mcorg.team.infrastructure.repository;

import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.infrastructure.repository.mappers.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TeamRepository implements Teams {

    private final MongoTeamRepository repository;

    @Override
    public Optional<Team> get(String id) {
        return repository.findById(id)
                .map(TeamMapper::toDomain);
    }

    @Override
    public List<Team> getTeamsWithUser(String username) {
        return repository.findAllByUsersContainingIgnoreCase(username)
                .stream()
                .map(TeamMapper::toDomain)
                .toList();
    }

    @Override
    public List<Team> getTeamsInWorld(String worldId) {
        return repository.findAllByWorldId(worldId)
                .stream()
                .map(TeamMapper::toDomain)
                .toList();
    }
}
