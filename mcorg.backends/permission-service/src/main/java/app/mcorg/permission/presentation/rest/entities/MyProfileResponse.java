package app.mcorg.permission.presentation.rest.entities;

import app.mcorg.permission.domain.model.permission.Profile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MyProfile")
public record MyProfileResponse(String minecraftId, String username) {
    public static MyProfileResponse from(Profile profile) {
        return new MyProfileResponse(profile.minecraftId(), profile.username());
    }
}
