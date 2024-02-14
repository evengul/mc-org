package app.mcorg.project.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.common.event.team.TeamEvent;
import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.infrastructure.entities.mappers.TeamMapper;
import app.mcorg.project.infrastructure.repository.MongoTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamUnitOfWork implements UnitOfWork<Team> {
    private final MongoTeamRepository repository;
    private final EventDispatcher<TeamEvent> dispatcher;

    @Override
    public Team add(Team aggregateRoot) {
        Team stored = TeamMapper.toDomain(repository.save(TeamMapper.toEntity(aggregateRoot)));
        dispatcher.dispatch(aggregateRoot.getDomainEvents());
        return stored;
    }

    @Override
    public void remove(String id) {
        repository.deleteById(id);
        dispatcher.dispatch(new TeamDeleted(id));
    }
}
