package app.mcorg.world.presentation.rest.entities.team;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorldTeam")
public record SlimTeamResponse(String id, String name) {
}
