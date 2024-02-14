package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.team.Team;

import java.util.List;

public interface Teams {
    List<Team> getAll(String username);

    Team get(String id);
}
