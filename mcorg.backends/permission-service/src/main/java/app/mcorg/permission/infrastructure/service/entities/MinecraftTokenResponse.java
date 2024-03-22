package app.mcorg.permission.infrastructure.service.entities;

import java.util.UUID;

public record MinecraftTokenResponse(UUID username, String access_token) {
}
