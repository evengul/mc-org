package app.mcorg.permission.infrastructure.service.entities;

public record MinecraftProfileNotFoundResponse(String path,
                                               String errorType,
                                               String error,
                                               String errorMessage,
                                               String developerMessage) {
}
