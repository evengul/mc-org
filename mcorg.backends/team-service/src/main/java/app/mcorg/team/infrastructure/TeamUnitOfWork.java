package app.mcorg.team.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.common.event.team.TeamEvent;
import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.infrastructure.repository.MongoTeamRepository;
import app.mcorg.team.infrastructure.repository.mappers.TeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamUnitOfWork implements UnitOfWork<Team> {

    private final EventDispatcher<TeamEvent> dispatcher;
    private final MongoTeamRepository repository;

    @Override
    public Team add(Team aggregateRoot) {
        Team stored = TeamMapper.toDomain(
                repository.save(TeamMapper.toEntity(aggregateRoot))
        );
        dispatcher.dispatch(aggregateRoot.getDomainEvents());
        return stored;
    }

    @Override
    public void remove(String id) {
        repository.findById(id)
                .ifPresent(team -> {
                    repository.deleteById(team.getId());
                    dispatcher.dispatch(new TeamDeleted(team.getId(), team.getWorldId()));
                });
    }
}
