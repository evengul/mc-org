package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.domain.api.Teams;
import app.mcorg.project.domain.exceptions.NotFoundException;
import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.infrastructure.entities.mappers.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TeamRepositoryImpl implements Teams {
    private final MongoTeamRepository repository;

    @Override
    public List<Team> getAll(String username) {
        return repository.findAllByUsers_Username(username)
                         .stream()
                         .map(TeamMapper::toDomain)
                         .toList();
    }

    @Override
    public Team get(String id) {
        return repository.findById(id)
                         .map(TeamMapper::toDomain)
                         .orElseThrow(() -> new NotFoundException(id));
    }
}
