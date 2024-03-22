package app.mcorg.permission.infrastructure.service.entities;

public record MinecraftRequest(String identityToken) {
    public static MinecraftRequest create(String userHash, String xstsToken) {
        return new MinecraftRequest(String.format("XBL3.0 x=%s;%s", userHash, xstsToken));
    }
}
