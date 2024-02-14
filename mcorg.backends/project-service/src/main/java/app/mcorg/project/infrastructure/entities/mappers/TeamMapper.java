package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.project.domain.model.team.Team;
import app.mcorg.project.infrastructure.entities.TeamEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TeamMapper {
    public static TeamEntity toEntity(Team team) {
        return new TeamEntity(
                team.getId(),
                team.getName(),
                team.getWorld(),
                team.getProjects(),
                team.getUsers()
        );
    }

    public static Team toDomain(TeamEntity entity) {
        return new Team(
                entity.getId(),
                entity.getName(),
                entity.getWorld(),
                entity.getProjects(),
                entity.getUsers()
        );
    }
}
