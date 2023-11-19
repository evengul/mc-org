package app.mcorg.team.infrastructure.repository.mappers;

import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.infrastructure.repository.entities.TeamEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TeamMapper {
    public static TeamEntity toEntity(Team team) {
        return new TeamEntity(team.getId(), team.getWorldId(), team.getName(), team.getUsers(), team.getProjects());
    }

    public static Team toDomain(TeamEntity entity) {
        return new Team(entity.getId(), entity.getWorldId(), entity.getName(), entity.getUsers(), entity.getProjects());
    }
}
