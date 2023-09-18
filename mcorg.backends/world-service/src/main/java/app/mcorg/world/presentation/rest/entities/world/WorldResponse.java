package app.mcorg.world.presentation.rest.entities.world;

import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.presentation.rest.entities.permission.SlimUserResponse;
import app.mcorg.world.presentation.rest.entities.team.SlimTeamResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "World")
public record WorldResponse(String id, String name, List<SlimUserResponse> users, List<SlimTeamResponse> teams) {
    public static WorldResponse from(World world) {
        return new WorldResponse(
                world.getId(),
                world.getName(),
                world.getUsers()
                        .stream()
                        .map(user -> new SlimUserResponse(user.username(), user.name()))
                        .toList(),
                world.getTeams()
                        .stream()
                        .map(team -> new SlimTeamResponse(team.id(), team.name()))
                        .toList()
        );
    }
}
