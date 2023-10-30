package app.mcorg.team.domain.api;

import app.mcorg.team.domain.model.team.Team;

import java.util.List;
import java.util.Optional;

public interface Teams {
    Optional<Team> get(String id);

    List<Team> getTeamsWithUser(String username);

    List<Team> getTeamsInWorld(String worldId);
}
