package app.mcorg.world.presentation.rest.entities.permission;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorldUser")
public record SlimUserResponse(String username, String name) {
}
