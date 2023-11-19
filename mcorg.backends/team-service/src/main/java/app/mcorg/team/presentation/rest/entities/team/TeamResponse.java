package app.mcorg.team.presentation.rest.entities.team;

import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.presentation.rest.entities.project.SlimProjectResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Team")
public record TeamResponse(String worldId, String id, List<String> users, List<SlimProjectResponse> projects) {
    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getWorldId(),
                team.getId(),
                team.getUsers(),
                team.getProjects()
                        .stream()
                        .map(project -> new SlimProjectResponse(project.id(), project.name()))
                        .toList()
        );
    }
}
